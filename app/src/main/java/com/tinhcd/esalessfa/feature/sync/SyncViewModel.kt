package com.tinhcd.esalessfa.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.tinhcd.esalessfa.core.datastore.SessionManager
import com.tinhcd.esalessfa.core.sync.SyncDownloadWorker
import com.tinhcd.esalessfa.core.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isRunning: Boolean = false,
    val page: Int = 0,
    val totalRows: Int = 0,
    val currentTable: String? = null,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _hasNavigated = MutableStateFlow(false)
    val hasNavigated: StateFlow<Boolean> = _hasNavigated.asStateFlow()

    /**
     * Trạng thái lấy từ WorkManager chứ không giữ trong ViewModel.
     *
     * Nhờ vậy đóng app giữa chừng rồi mở lại vẫn thấy đúng tiến trình đang chạy —
     * ViewModel bị huỷ nhưng công việc thì không.
     */
    val uiState: StateFlow<SyncUiState> = syncManager.observeDownload()
        .map { info -> info.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun startSync() = syncManager.startDownload()

    fun retry() = syncManager.startDownload(force = true)

    fun onSyncCompleted() {
        viewModelScope.launch {
            sessionManager.markFirstSyncCompleted()
            _hasNavigated.value = true
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
}
