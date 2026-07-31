package com.tinhcd.esalessfa.domain.repository

/**
 * Kết quả một lượt đẩy ảnh, đủ để worker quyết định thử lại hay thôi.
 */
data class PhotoUploadOutcome(
    val uploaded: Int,
    /** Còn ảnh lỗi có thể thử lại (mất mạng, server 5xx). */
    val hasRetryableFailure: Boolean,
)

/**
 * Hàng đợi ảnh minh chứng chờ lên Storage.
 *
 * Worker chỉ gọi [uploadPending] rồi dịch kết quả sang Result của WorkManager;
 * toàn bộ việc đọc hàng đợi, đẩy file và cập nhật trạng thái nằm ở tầng data —
 * cùng cách SyncWorker đi qua SyncRepository, thay vì worker tự cầm DAO.
 */
interface PhotoUploadRepository {

    suspend fun uploadPending(): PhotoUploadOutcome
}
