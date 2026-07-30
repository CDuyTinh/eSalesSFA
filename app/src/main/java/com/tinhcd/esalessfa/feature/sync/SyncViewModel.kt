package com.tinhcd.esalessfa.feature.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import com.tinhcd.esalessfa.core.datastore.SessionManager
import com.tinhcd.esalessfa.core.sync.FullSyncHandle
import com.tinhcd.esalessfa.core.sync.SyncDownloadWorker
import com.tinhcd.esalessfa.core.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hai đường vào màn đồng bộ.
 *
 * Cùng một màn hình vì tiến trình và cách báo lỗi giống nhau; chỉ khác việc cần
 * chạy và nơi đi tiếp sau khi xong.
 */
enum class SyncMode {
    /** Sau đăng nhập: chỉ tải xuống (outbox còn rỗng), xong thì vào Home. */
    FIRST_RUN,

    /** Người dùng bấm Đồng bộ ở Home: gửi lên rồi tải xuống, xong thì quay lại. */
    MANUAL,
}

data class SyncUiState(
    val isRunning: Boolean = false,
    val page: Int = 0,
    val totalRows: Int = 0,
    val currentTable: String? = null,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val mode: SyncMode = savedStateHandle.get<String>(ARG_MODE)
        ?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() }
        ?: SyncMode.FIRST_RUN

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val manualHandle = MutableStateFlow<FullSyncHandle?>(null)

    /**
     * Trạng thái lấy từ WorkManager chứ không giữ trong ViewModel.
     *
     * Nhờ vậy đóng app giữa chừng rồi mở lại vẫn thấy đúng tiến trình đang chạy —
     * ViewModel bị huỷ nhưng công việc thì không.
     */
    val uiState: StateFlow<SyncUiState> = when (mode) {
        SyncMode.FIRST_RUN -> syncManager.observeDownload().map { it.toUiState() }

        SyncMode.MANUAL -> manualHandle.flatMapLatest { handle ->
            if (handle == null) {
                flowOf(SyncUiState())
            } else {
                syncManager.observeFullSync(handle).map { it.toChainUiState(handle) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    /**
     * Màn này là nơi DUY NHẤT khởi động đồng bộ từ giao diện.
     *
     * Trước đó Home tự gọi startFullSync rồi ở lại chỗ cũ, nên người dùng bấm mà
     * không thấy gì xảy ra. Gom về đây thì mọi lượt đồng bộ do người dùng chủ động
     * đều có màn hình tiến trình.
     */
    fun start() {
        when (mode) {
            // KEEP bên trong nên gọi lại lúc xoay máy không tạo thêm lượt nào.
            SyncMode.FIRST_RUN -> syncManager.startDownload()

            // Chuỗi đầy đủ dùng APPEND_OR_REPLACE nên phải tự chặn gọi trùng:
            // Fragment gọi start() lại mỗi lần view được tạo lại.
            SyncMode.MANUAL -> if (manualHandle.value == null) {
                manualHandle.value = syncManager.startFullSync()
            }
        }
    }

    fun retry() {
        when (mode) {
            SyncMode.FIRST_RUN -> syncManager.startDownload(force = true)
            SyncMode.MANUAL -> manualHandle.value = syncManager.startFullSync()
        }
    }

    fun onSyncCompleted() {
        viewModelScope.launch {
            if (mode == SyncMode.FIRST_RUN) sessionManager.markFirstSyncCompleted()
            _finished.value = true
        }
    }

    private fun WorkInfo?.toUiState(): SyncUiState {
        if (this == null) return SyncUiState()

        val (page, rows, table) = SyncDownloadWorker.progressOf(progress)

        return when (state) {
            WorkInfo.State.RUNNING -> SyncUiState(
                isRunning = true,
                page = page,
                totalRows = rows,
                currentTable = table,
            )

            WorkInfo.State.SUCCEEDED -> SyncUiState(
                isCompleted = true,
                page = outputData.getInt(SyncDownloadWorker.KEY_PAGE, page),
                totalRows = outputData.getInt(SyncDownloadWorker.KEY_TOTAL_ROWS, rows),
            )

            WorkInfo.State.FAILED -> SyncUiState(
                errorMessage = outputData.getString(SyncDownloadWorker.KEY_ERROR)
                    ?: "Đồng bộ thất bại",
            )

            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncUiState(isRunning = true)

            WorkInfo.State.CANCELLED -> SyncUiState(errorMessage = "Đã huỷ đồng bộ")
        }
    }

    /**
     * Gộp trạng thái của cả chuỗi gửi-lên-rồi-tải-xuống.
     *
     * Danh sách chỉ chứa hai việc của đúng lượt này, nhưng WorkManager không hứa
     * thứ tự nên phải tìm việc tải xuống theo id thay vì lấy phần tử cuối.
     */
    private fun List<WorkInfo>.toChainUiState(handle: FullSyncHandle): SyncUiState {
        // Chưa thấy việc nào tức là WorkManager chưa ghi xong — vẫn coi là đang
        // chạy để màn hình không nhá qua trạng thái rỗng.
        if (isEmpty()) return SyncUiState(isRunning = true)

        firstOrNull { it.state == WorkInfo.State.FAILED }?.let { failed ->
            return SyncUiState(
                errorMessage = failed.outputData.getString(SyncDownloadWorker.KEY_ERROR)
                    ?: "Đồng bộ thất bại",
            )
        }
        if (any { it.state == WorkInfo.State.CANCELLED }) {
            return SyncUiState(errorMessage = "Đã huỷ đồng bộ")
        }

        val download = firstOrNull { it.id == handle.downloadId }

        // Chỉ báo xong khi CẢ HAI việc đã xong. Việc tải xuống chạy sau việc gửi
        // lên, nên xét từng việc riêng sẽ báo xong quá sớm.
        if (size == 2 && all { it.state == WorkInfo.State.SUCCEEDED }) {
            return SyncUiState(
                isCompleted = true,
                page = download?.outputData?.getInt(SyncDownloadWorker.KEY_PAGE, 0) ?: 0,
                totalRows = download?.outputData?.getInt(SyncDownloadWorker.KEY_TOTAL_ROWS, 0) ?: 0,
            )
        }

        // Số trang/số dòng chỉ có ý nghĩa ở bước tải xuống; bước gửi lên chạy
        // nhanh nên để trống là đủ.
        val (page, rows, table) = SyncDownloadWorker.progressOf(
            download?.progress ?: Data.EMPTY
        )
        return SyncUiState(isRunning = true, page = page, totalRows = rows, currentTable = table)
    }

    companion object {
        const val ARG_MODE = "mode"
    }
}
