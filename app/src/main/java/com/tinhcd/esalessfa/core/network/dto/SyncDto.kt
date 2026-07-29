package com.tinhcd.esalessfa.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// =============================================================================
// DOWNLOAD
// =============================================================================

@Serializable
data class SyncDownloadRequest(
    @SerialName("session_id") val sessionId: String,
    /** Mốc version client đang giữ cho từng bảng: `{"customers": 1042, "products": 998}` */
    @SerialName("versions") val versions: Map<String, Long>,
    /** Giới hạn số dòng mỗi bảng cho một lần gọi; client lặp tới khi `has_more = false`. */
    @SerialName("page_size") val pageSize: Int = 1000,
)

@Serializable
data class SyncDownloadResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("tables") val tables: Map<String, TableChangeSet>,
    /** Còn dữ liệu chưa lấy hết -> client gọi lại với version mới. */
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("server_time") val serverTime: String,
)

@Serializable
data class TableChangeSet(
    /**
     * Dòng thô, giữ nguyên dạng JSON. Tầng data mới decode sang Entity cụ thể —
     * nhờ vậy thêm bảng mới không phải sửa lớp response này.
     */
    @SerialName("rows") val rows: List<JsonElement>,
    /** Version lớn nhất trong lô này; client lưu vào `sync_state` sau khi ghi xong. */
    @SerialName("max_version") val maxVersion: Long,
    @SerialName("deleted_ids") val deletedIds: List<String> = emptyList(),
)

// =============================================================================
// UPLOAD
// =============================================================================

@Serializable
data class SyncUploadRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("visits") val visits: List<JsonElement> = emptyList(),
    @SerialName("orders") val orders: List<OrderUploadDto> = emptyList(),
    @SerialName("stock_counts") val stockCounts: List<StockCountUploadDto> = emptyList(),
    @SerialName("surveys") val surveys: List<SurveyUploadDto> = emptyList(),
)

/** Bài khảo sát gửi kèm câu trả lời và ảnh minh chứng đã upload xong. */
@Serializable
data class SurveyUploadDto(
    @SerialName("header") val header: JsonElement,
    @SerialName("answers") val answers: List<JsonElement>,
    @SerialName("photos") val photos: List<JsonElement> = emptyList(),
)

/** Phiếu kiểm kê gửi kèm các dòng đã đếm. */
@Serializable
data class StockCountUploadDto(
    @SerialName("header") val header: JsonElement,
    @SerialName("details") val details: List<JsonElement>,
)

/** Đơn hàng gửi kèm chi tiết và khuyến mãi — server ghi cả cụm trong một transaction. */
@Serializable
data class OrderUploadDto(
    @SerialName("order") val order: JsonElement,
    @SerialName("details") val details: List<JsonElement>,
    @SerialName("promotions") val promotions: List<JsonElement> = emptyList(),
)

@Serializable
data class SyncUploadResponse(
    @SerialName("session_id") val sessionId: String,
    /** Id server đã ghi nhận -> client đánh dấu SYNCED. */
    @SerialName("accepted") val accepted: List<String> = emptyList(),
    /** Id server từ chối vì lỗi nghiệp vụ -> client đánh dấu FAILED, KHÔNG retry. */
    @SerialName("rejected") val rejected: List<RejectedItem> = emptyList(),
)

@Serializable
data class RejectedItem(
    @SerialName("id") val id: String,
    @SerialName("entity") val entity: String,
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
)
