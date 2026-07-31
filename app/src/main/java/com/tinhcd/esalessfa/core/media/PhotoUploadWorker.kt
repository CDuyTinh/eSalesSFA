package com.tinhcd.esalessfa.core.media

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinhcd.esalessfa.domain.repository.PhotoUploadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Vỏ WorkManager cho hàng đợi ảnh minh chứng.
 *
 * Tách khỏi worker đồng bộ dữ liệu vì hai việc có đặc tính khác hẳn: một batch
 * JSON vài trăm KB gửi một lần là xong, còn ảnh thì nặng, chậm, và hay đứt giữa
 * chừng. Trộn chung sẽ khiến một tấm ảnh lỗi chặn luôn cả đơn hàng.
 *
 * Worker không cầm DAO và không biết Storage: nó gọi [PhotoUploadRepository] rồi
 * dịch kết quả sang Result, đúng như SyncWorker đi qua SyncRepository.
 */
@HiltWorker
class PhotoUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PhotoUploadRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = repository.uploadPending()

        // Còn ảnh lỗi thì để WorkManager thử lại theo backoff, thay vì báo thành
        // công và bỏ quên chúng.
        return if (outcome.hasRetryableFailure) Result.retry() else Result.success()
    }
}
