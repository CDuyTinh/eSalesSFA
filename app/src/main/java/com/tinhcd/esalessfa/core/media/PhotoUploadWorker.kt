package com.tinhcd.esalessfa.core.media

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.SurveyResultDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.storage.Storage
import java.io.File

/**
 * Đẩy ảnh minh chứng lên Supabase Storage.
 *
 * Tách khỏi worker đồng bộ dữ liệu vì hai việc có đặc tính khác hẳn: một batch
 * JSON vài trăm KB gửi một lần là xong, còn ảnh thì nặng, chậm, và hay đứt giữa
 * chừng. Trộn chung sẽ khiến một tấm ảnh lỗi chặn luôn cả đơn hàng.
 *
 * Ảnh đi THẲNG lên Storage chứ không qua Edge Function — đẩy file nhị phân qua
 * function vừa chậm vừa chạm giới hạn payload.
 */
@HiltWorker
class PhotoUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pendingUploadDao: PendingUploadDao,
    private val surveyResultDao: SurveyResultDao,
    private val storage: Storage,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = pendingUploadDao.getPending()
        if (pending.isEmpty()) return Result.success()

        var hasRetryableFailure = false

        for (item in pending) {
            val file = File(item.localPath)

            // File bị xoá (người dùng dọn bộ nhớ, hoặc cache bị hệ thống thu hồi)
            // thì không bao giờ upload được — đánh dấu hỏng thay vì retry mãi.
            if (!file.exists()) {
                pendingUploadDao.updateStatus(item.id, STATUS_FAILED, "File không còn tồn tại")
                continue
            }

            pendingUploadDao.updateStatus(item.id, STATUS_UPLOADING, null)

            val outcome = runCatching {
                storage.from(item.bucket).upload(item.remotePath, file.readBytes()) {
                    upsert = true
                }
            }

            if (outcome.isSuccess) {
                pendingUploadDao.updateStatus(item.id, STATUS_DONE, null)
                // Ghi storagePath vào bản ghi ảnh: chỉ khi có giá trị này thì
                // bài khảo sát mới đủ điều kiện đẩy lên server.
                surveyResultDao.markPhotoUploaded(item.entityId, item.remotePath)
                file.delete()
            } else {
                hasRetryableFailure = true
                pendingUploadDao.updateStatus(
                    item.id,
                    STATUS_FAILED,
                    outcome.exceptionOrNull()?.message,
                )
            }
        }

        pendingUploadDao.clearDone()

        // Còn ảnh lỗi thì để WorkManager thử lại theo backoff, thay vì báo thành
        // công và bỏ quên chúng.
        return if (hasRetryableFailure) Result.retry() else Result.success()
    }

    companion object {
        const val BUCKET_SURVEY_PHOTOS = "survey-photos"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
