package com.tinhcd.esalessfa.feature.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.sync.SyncPhase
import com.tinhcd.esalessfa.domain.model.sync.SyncRun
import com.tinhcd.esalessfa.domain.model.sync.SyncRunStatus
import com.tinhcd.esalessfa.domain.model.sync.SyncTables
import com.tinhcd.esalessfa.domain.repository.SessionStore
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Ba đường vào màn đồng bộ.
 *
 * Cùng một màn hình vì tiến trình và cách báo lỗi giống nhau; chỉ khác việc cần
 * chạy và nơi đi tiếp sau khi xong.
 */
enum class SyncMode {
    /** Sau đăng nhập: chỉ tải xuống (outbox còn rỗng), xong thì vào Home. */
    FIRST_RUN,

    /**
     * Mở app lần đầu trong ngày mới: gửi lên rồi tải xuống, xong thì vào Home.
     *
     * Phải gửi lên trước chứ không chỉ tải xuống như [FIRST_RUN]: đơn chốt cuối
     * ngày hôm qua lúc mất sóng vẫn còn nằm trong outbox, mà lượt tải xuống sẽ
     * ghi đè giá và khuyến mãi mà những đơn đó tham chiếu tới.
     */
    DAILY,

    /**
     * Vào từ Home: gửi lên rồi tải xuống, xong thì quay lại chỗ vừa đứng.
     *
     * Dùng cho cả lượt người dùng bấm Đồng bộ lẫn lượt Home tự mở khi phát hiện
     * app đã nằm mở qua nửa đêm — hai trường hợp chỉ khác ai kích hoạt.
     */
    MANUAL;

    /** Có chạy chuỗi gửi-lên-rồi-tải-xuống không, hay chỉ tải xuống. */
    val isFullSync: Boolean get() = this != FIRST_RUN
}

data class SyncUiState(
    val isRunning: Boolean = false,
    val phase: SyncPhase = SyncPhase.DOWNLOAD,
    val percent: Int = 0,
    val partsDone: Int = 0,
    val partsTotal: Int = SyncTables.ALL.size,
    val doneTables: Set<String> = emptySet(),
    val page: Int = 0,
    val totalRows: Int = 0,
    val currentTable: String? = null,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val sessionStore: SessionStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val mode: SyncMode = savedStateHandle.get<String>(ARG_MODE)
        ?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() }
        ?: SyncMode.FIRST_RUN

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** Luồng trạng thái của lượt đầy đủ đang chạy; null là chưa khởi động lần nào. */
    private val fullRun = MutableStateFlow<Flow<SyncRun>?>(null)

    /**
     * Trạng thái lấy từ tầng lịch chạy chứ không giữ trong ViewModel.
     *
     * Nhờ vậy đóng app giữa chừng rồi mở lại vẫn thấy đúng tiến trình đang chạy —
     * ViewModel bị huỷ nhưng công việc thì không.
     */
    val uiState: StateFlow<SyncUiState> = if (mode.isFullSync) {
        fullRun.flatMapLatest { run ->
            run?.map { it.toUiState() } ?: flowOf(SyncUiState())
        }
    } else {
        syncScheduler.observeDownload().map { it.toUiState() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    /**
     * Màn này là nơi DUY NHẤT khởi động đồng bộ từ giao diện.
     *
     * Trước đó Home tự gọi startFullSync rồi ở lại chỗ cũ, nên người dùng bấm mà
     * không thấy gì xảy ra. Gom về đây thì mọi lượt đồng bộ do người dùng chủ động
     * đều có màn hình tiến trình.
     */
    fun start() {
        if (mode.isFullSync) {
            // Chuỗi đầy đủ thay thế lượt cũ nên phải tự chặn gọi trùng: Fragment
            // gọi start() lại mỗi lần view được tạo lại.
            if (fullRun.value == null) fullRun.value = syncScheduler.startFullSync()
        } else {
            // Bên trong dùng KEEP nên gọi lại lúc xoay máy không tạo thêm lượt nào.
            syncScheduler.startDownload()
        }
    }

    fun retry() {
        if (mode.isFullSync) {
            fullRun.value = syncScheduler.startFullSync()
        } else {
            syncScheduler.startDownload(force = true)
        }
    }

    /**
     * Đóng dấu ngày sync cho MỌI lượt, kể cả lượt người dùng tự bấm.
     *
     * Ai đã chủ động đồng bộ lúc 9h sáng thì mở lại app buổi chiều không có lý do
     * gì bị chặn ở màn đồng bộ lần nữa — bản Java cũ cũng ghi mốc SYNC_START sau
     * mọi lượt chứ không riêng lượt đăng nhập.
     */
    fun onSyncCompleted() {
        viewModelScope.launch {
            sessionStore.markSyncCompleted(LocalDate.now())
            _finished.value = true
        }
    }

    /**
     * Chữ báo lỗi chọn ở đây chứ không ở tầng dưới: [SyncRun] chỉ mang lời server
     * trả về, còn câu hiển thị khi không có lời nào là việc của giao diện.
     */
    private fun SyncRun.toUiState(): SyncUiState = when (status) {
        SyncRunStatus.IDLE -> SyncUiState()

        SyncRunStatus.RUNNING -> SyncUiState(
            isRunning = true,
            phase = phase,
            percent = percent,
            partsDone = doneTables.size,
            doneTables = doneTables,
            page = page,
            totalRows = totalRows,
            currentTable = currentTable,
        )

        SyncRunStatus.SUCCEEDED -> SyncUiState(
            isCompleted = true,
            percent = 100,
            partsDone = SyncTables.ALL.size,
            doneTables = SyncTables.ALL.toSet(),
            page = page,
            totalRows = totalRows,
        )

        SyncRunStatus.FAILED -> SyncUiState(errorMessage = errorMessage ?: "Đồng bộ thất bại")

        SyncRunStatus.CANCELLED -> SyncUiState(errorMessage = "Đã huỷ đồng bộ")
    }

    companion object {
        const val ARG_MODE = "mode"
    }
}
