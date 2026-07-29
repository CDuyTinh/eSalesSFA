package com.tinhcd.esalessfa.core.database.entity.transaction

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tinhcd.esalessfa.core.database.SyncStatus

// =============================================================================
// Kiểm kê tồn kho cửa hàng
// =============================================================================

@Entity(
    tableName = "stock_counts",
    indices = [
        Index(value = ["salespersonId", "countDate"]),
        Index("customerId"),
        Index("syncStatus"),
    ],
)
data class StockCountEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val salespersonId: String,
    val visitId: String?,
    val countDate: String,
    val note: String?,

    val syncStatus: SyncStatus = SyncStatus.DRAFT,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    val sessionId: String? = null,
    val clientCreatedAt: Long,
    val serverAckAt: Long? = null,
)

@Entity(
    tableName = "stock_count_details",
    indices = [
        Index("stockCountId"),
        Index(value = ["stockCountId", "productId", "uomCode"], unique = true),
    ],
)
data class StockCountDetailEntity(
    @PrimaryKey val id: String,
    val stockCountId: String,
    val productId: String,
    val uomCode: String,
    val qty: Double,
    val baseQty: Double,
    /** Tồn kỳ trước, để báo cáo so sánh mà không phải tự tra lịch sử. */
    val prevBaseQty: Double,
    /** Số lượng gợi ý đặt hàng, tính từ chênh lệch so với kỳ trước. */
    val suggestQty: Double,
)

// =============================================================================
// Kết quả khảo sát
// =============================================================================

@Entity(
    tableName = "surveys",
    indices = [
        Index(value = ["salespersonId", "surveyDate"]),
        Index("customerId"),
        Index("syncStatus"),
    ],
)
data class SurveyEntity(
    @PrimaryKey val id: String,
    val surveyTypeId: String,
    val customerId: String,
    val salespersonId: String,
    val visitId: String?,
    val surveyDate: String,
    val totalScore: Double,
    val isPassed: Boolean,
    val note: String?,

    val syncStatus: SyncStatus = SyncStatus.DRAFT,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    val sessionId: String? = null,
    val clientCreatedAt: Long,
    val serverAckAt: Long? = null,
)

@Entity(
    tableName = "survey_answers",
    indices = [Index("surveyId"), Index("questionId")],
)
data class SurveyAnswerEntity(
    @PrimaryKey val id: String,
    val surveyId: String,
    val questionId: String,
    /** Chỉ dùng cho câu SINGLE; câu MULTI sinh nhiều dòng, mỗi dòng một đáp án. */
    val optionId: String?,
    val answerText: String?,
    val answerValue: Double?,
    val answerBool: Boolean?,
    val score: Double,
)

/**
 * Ảnh minh chứng.
 *
 * [localPath] chỉ tồn tại ở client — nó là đường dẫn file trên máy trong lúc chờ
 * upload. Sau khi lên Storage xong thì [storagePath] mới có giá trị và bản ghi
 * mới đủ điều kiện đẩy lên server.
 */
@Entity(
    tableName = "survey_photos",
    indices = [Index("surveyId")],
)
data class SurveyPhotoEntity(
    @PrimaryKey val id: String,
    val surveyId: String,
    val questionId: String?,
    val localPath: String,
    val storagePath: String?,
    val latitude: Double?,
    val longitude: Double?,
    val takenAt: Long,
    val fileSize: Int,
)
