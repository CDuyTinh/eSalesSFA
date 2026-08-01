package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.common.dispatcher.DispatcherProvider
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.SurveyResultDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.domain.repository.PhotoUploadOutcome
import com.tinhcd.esalessfa.domain.repository.PhotoUploadRepository
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Đẩy ảnh minh chứng lên Supabase Storage.
 *
 * Ảnh đi THẲNG lên Storage chứ không qua Edge Function — đẩy file nhị phân qua
 * function vừa chậm vừa chạm giới hạn payload.
 *
 * Toàn bộ vòng lặp nằm ở đây thay vì trong worker: worker chỉ là cái vỏ của
 * WorkManager, còn quy tắc "ảnh nào coi là hỏng hẳn, ảnh nào đáng thử lại" là
 * chuyện dữ liệu và phải test được mà không cần WorkManager.
 */
class PhotoUploadRepositoryImpl @Inject constructor(
    private val pendingUploadDao: PendingUploadDao,
    private val surveyResultDao: SurveyResultDao,
    private val visitDao: VisitDao,
    private val storage: Storage,
    private val dispatchers: DispatcherProvider,
) : PhotoUploadRepository {

    override suspend fun uploadPending(): PhotoUploadOutcome = withContext(dispatchers.io) {
        val pending = pendingUploadDao.getPending()
        if (pending.isEmpty()) return@withContext PhotoUploadOutcome(0, false)

        var uploaded = 0
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
                // Ghi đường dẫn trên Storage về đúng bản ghi cha. Với khảo sát,
                // chỉ khi có giá trị này thì bài mới đủ điều kiện đẩy lên server.
                when (item.entityType) {
                    VisitRepositoryImpl.ENTITY_VISIT_PHOTO ->
                        visitDao.markPhotoUploaded(item.entityId, item.remotePath)

                    else -> surveyResultDao.markPhotoUploaded(item.entityId, item.remotePath)
                }
                file.delete()
                uploaded++
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

        PhotoUploadOutcome(uploaded = uploaded, hasRetryableFailure = hasRetryableFailure)
    }

    companion object {
        const val BUCKET_SURVEY_PHOTOS = "survey-photos"
        const val BUCKET_VISIT_PHOTOS = "visit-photos"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADING = "UPLOADING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
