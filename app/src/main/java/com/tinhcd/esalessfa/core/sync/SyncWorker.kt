package com.tinhcd.esalessfa.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import com.tinhcd.esalessfa.domain.sync.SyncProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

/**
 * Tải master data ở nền.
 *
 * Chạy trong WorkManager thay vì coroutine của ViewModel vì lượt sync đầu ca kéo
 * hàng nghìn dòng qua nhiều trang: user rời màn hình hay xoay máy giữa chừng
 * không được làm hỏng nó.
 */
@HiltWorker
class SyncDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var outcome: Result = Result.success()

        syncRepository.downloadMasterData().collect { progress ->
            when (progress) {
                is SyncProgress.Downloading -> setProgress(
                    workDataOf(
                        KEY_PAGE to progress.page,
                        KEY_TOTAL_ROWS to progress.totalRows,
                        KEY_TABLE to progress.currentTable,
                    )
                )

                is SyncProgress.Completed -> outcome = Result.success(
                    workDataOf(
                        KEY_TOTAL_ROWS to progress.totalRows,
                        KEY_PAGE to progress.pages,
                        KEY_DURATION to progress.durationMs,
                    )
                )

                is SyncProgress.Failed -> {
                    val data = workDataOf(KEY_ERROR to progress.message)
                    // Chỉ retry khi lỗi có khả năng tự khỏi (mạng, 5xx). Lỗi 4xx
                    // như token hỏng hay chưa nối nhân viên thì retry chỉ tốn pin
                    // và làm log nhiễu — phải để user thấy và xử lý.
                    outcome = if (progress.isRetryable) Result.retry() else Result.failure(data)
                }

                else -> Unit
            }
        }

        return outcome
    }

    companion object {
        const val KEY_PAGE = "page"
        const val KEY_TOTAL_ROWS = "total_rows"
        const val KEY_TABLE = "table"
        const val KEY_DURATION = "duration_ms"
        const val KEY_ERROR = "error"

        fun progressOf(data: Data): Triple<Int, Int, String?> = Triple(
            data.getInt(KEY_PAGE, 0),
            data.getInt(KEY_TOTAL_ROWS, 0),
            data.getString(KEY_TABLE),
        )
    }
}

/**
 * Đẩy giao dịch trong outbox lên server.
 *
 * Tách khỏi worker tải xuống vì hai việc có ràng buộc khác nhau: tải xuống có
 * thể huỷ và chạy lại bất cứ lúc nào, còn gửi lên phải chạy tới nơi để đơn hàng
 * của nhân viên không kẹt lại trong máy.
 */
@HiltWorker
class SyncUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var outcome: Result = Result.success()

        syncRepository.uploadPending().collect { progress ->
            when (progress) {
                is SyncProgress.Uploading -> setProgress(
                    workDataOf(
                        SyncDownloadWorker.KEY_TOTAL_ROWS to progress.total,
                    )
                )

                is SyncProgress.Completed -> outcome = Result.success(
                    workDataOf(SyncDownloadWorker.KEY_TOTAL_ROWS to progress.totalRows)
                )

                is SyncProgress.Failed -> {
                    val data = workDataOf(SyncDownloadWorker.KEY_ERROR to progress.message)
                    outcome = if (progress.isRetryable) Result.retry() else Result.failure(data)
                }

                else -> Unit
            }
        }

        return outcome
    }
}
