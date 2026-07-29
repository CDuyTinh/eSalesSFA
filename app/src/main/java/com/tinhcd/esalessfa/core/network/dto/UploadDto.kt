package com.tinhcd.esalessfa.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// DTO đẩy giao dịch lên server.
//
// Tên trường phải khớp CHÍNH XÁC tên cột Postgres, vì Edge Function spread
// nguyên object vào câu upsert. Sai một tên là cột đó nhận null lặng lẽ.
//
// session_id và salesperson_id cố ý KHÔNG gửi: Edge Function tự ghi đè theo JWT
// để client không thể tạo đơn mang tên nhân viên khác.
// =============================================================================

@Serializable
data class OrderUploadBody(
    val id: String,
    @SerialName("order_no") val orderNo: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("branch_id") val branchId: String,
    @SerialName("order_date") val orderDate: String,
    @SerialName("delivery_date") val deliveryDate: String? = null,
    val status: String,
    @SerialName("sub_total") val subTotal: Long,
    @SerialName("discount_amount") val discountAmount: Long,
    @SerialName("manual_discount") val manualDiscount: Long,
    @SerialName("net_amount") val netAmount: Long,
    @SerialName("vat_amount") val vatAmount: Long,
    @SerialName("total_amount") val totalAmount: Long,
    val note: String? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
    /** ISO-8601; cột Postgres là timestamptz nên không nhận epoch millis. */
    @SerialName("client_created_at") val clientCreatedAt: String,
)

@Serializable
data class OrderDetailUploadBody(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("line_no") val lineNo: Int,
    @SerialName("product_id") val productId: String,
    @SerialName("uom_code") val uomCode: String,
    val qty: Double,
    @SerialName("conversion_rate") val conversionRate: Double,
    @SerialName("base_qty") val baseQty: Double,
    val price: Long,
    @SerialName("gross_amount") val grossAmount: Long,
    @SerialName("discount_amount") val discountAmount: Long,
    @SerialName("net_amount") val netAmount: Long,
    @SerialName("vat_rate") val vatRate: Double,
    @SerialName("vat_amount") val vatAmount: Long,
    @SerialName("line_amount") val lineAmount: Long,
    @SerialName("is_free_item") val isFreeItem: Boolean,
    @SerialName("promotion_id") val promotionId: String? = null,
)

@Serializable
data class OrderPromotionUploadBody(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("order_detail_id") val orderDetailId: String? = null,
    @SerialName("program_id") val programId: String,
    @SerialName("break_id") val breakId: String? = null,
    @SerialName("apply_times") val applyTimes: Double,
    @SerialName("discount_amount") val discountAmount: Long,
    @SerialName("free_qty") val freeQty: Double,
    @SerialName("is_manual") val isManual: Boolean,
)

@Serializable
data class VisitUploadBody(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("visit_date") val visitDate: String,
    @SerialName("is_in_route") val isInRoute: Boolean,
    @SerialName("check_in_at") val checkInAt: String,
    @SerialName("check_in_lat") val checkInLat: Double? = null,
    @SerialName("check_in_lng") val checkInLng: Double? = null,
    @SerialName("check_in_accuracy") val checkInAccuracy: Float? = null,
    @SerialName("check_in_distance") val checkInDistance: Float? = null,
    @SerialName("check_out_at") val checkOutAt: String? = null,
    @SerialName("check_out_lat") val checkOutLat: Double? = null,
    @SerialName("check_out_lng") val checkOutLng: Double? = null,
    @SerialName("check_out_distance") val checkOutDistance: Float? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
    val note: String? = null,
    @SerialName("is_mock_location") val isMockLocation: Boolean,
    @SerialName("battery_pct") val batteryPct: Int? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("client_created_at") val clientCreatedAt: String,
)

@Serializable
data class StockCountUploadBody(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("count_date") val countDate: String,
    val note: String? = null,
    @SerialName("client_created_at") val clientCreatedAt: String,
)

@Serializable
data class StockCountDetailUploadBody(
    val id: String,
    @SerialName("stock_count_id") val stockCountId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("uom_code") val uomCode: String,
    val qty: Double,
    @SerialName("base_qty") val baseQty: Double,
    @SerialName("prev_base_qty") val prevBaseQty: Double,
    @SerialName("suggest_qty") val suggestQty: Double,
)
