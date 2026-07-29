package com.tinhcd.esalessfa.core.database.entity.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// =============================================================================
// Bảng chỉ tồn tại ở client — server không có, không bao giờ đồng bộ.
// =============================================================================

/**
 * Xương sống của delta sync: mỗi bảng master một dòng, ghi mốc version đã tải tới.
 *
 * Lần sync sau gửi [lastVersion] lên, server trả về đúng phần thay đổi kể từ mốc
 * đó. Nhờ vậy không phải tải lại toàn bộ danh mục mỗi ngày.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val tableName: String,
    val lastVersion: Long = 0L,
    val lastSyncedAt: Long = 0L,
    val rowCount: Int = 0,
    val lastError: String? = null,
)

/**
 * Hàng đợi upload file (ảnh minh chứng khảo sát).
 *
 * Tách khỏi bản ghi nghiệp vụ vì ảnh nặng và hay lỗi giữa chừng: upload xong mới
 * cập nhật URL vào bản ghi cha, thất bại thì retry riêng phần file mà không ảnh
 * hưởng dữ liệu đã ghi.
 */
@Entity(
    tableName = "pending_uploads",
    indices = [Index("status"), Index("entityId")],
)
data class PendingUploadEntity(
    @PrimaryKey val id: String,
    /** Loại bản ghi cha, ví dụ "SURVEY_PHOTO". */
    val entityType: String,
    val entityId: String,
    val localPath: String,
    val bucket: String,
    val remotePath: String,
    val fileSize: Long,
    /** PENDING | UPLOADING | DONE | FAILED */
    val status: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
)

/** Nhật ký lỗi sync để hiển thị trong màn Cài đặt, giúp user tự chẩn đoán. */
@Entity(
    tableName = "sync_errors",
    indices = [Index("createdAt")],
)
data class SyncErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val tableName: String?,
    val entityId: String?,
    val message: String,
    val createdAt: Long,
)
