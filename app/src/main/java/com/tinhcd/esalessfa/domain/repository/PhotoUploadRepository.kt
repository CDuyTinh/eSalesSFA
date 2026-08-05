package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.photo.PhotoUploadOutcome

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
