package com.tinhcd.esalessfa.core.database.entity.transaction

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tinhcd.esalessfa.core.database.SyncStatus

// =============================================================================
// Giao dịch — do client tạo khi offline, sau đó đẩy lên server.
//
// id là UUID do CLIENT sinh (không phải server cấp). Đây là điều kiện để
// sync-upload idempotent: gửi lại cùng id thì server nhận ra và bỏ qua.
//
// Cột sync chỉ đặt trên bảng GỐC. Bảng con đi theo cha.
// =============================================================================

@Entity(
    tableName = "visits",
    indices = [
        Index(value = ["salespersonId", "visitDate"]),
        Index("customerId"),
        Index("syncStatus"),
    ],
)
data class VisitEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val salespersonId: String,
    /** "yyyy-MM-dd" */
    val visitDate: String,
    val isInRoute: Boolean,

    /** epoch millis */
    val checkInAt: Long,
    val checkInLat: Double?,
    val checkInLng: Double?,
    val checkInAccuracy: Float?,
    /** Khoảng cách tới toạ độ khách hàng, mét. Lưu lại để đối chiếu về sau. */
    val checkInDistance: Float?,

    val checkOutAt: Long?,
    val checkOutLat: Double?,
    val checkOutLng: Double?,
    val checkOutDistance: Float?,
    val durationMinutes: Int?,

    /** Bắt buộc có khi check-in vượt bán kính cho phép. */
    val reasonCode: String?,
    val note: String?,
    /** Cờ chống gian lận — Location.isMock trên Android 12+. */
    val isMockLocation: Boolean,
    val batteryPct: Int?,
    val deviceId: String?,

    // ── sync ──
    val syncStatus: SyncStatus = SyncStatus.DRAFT,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    val sessionId: String? = null,
    val clientCreatedAt: Long,
    val serverAckAt: Long? = null,
)

@Entity(
    tableName = "orders",
    indices = [
        Index("orderNo", unique = true),
        Index(value = ["salespersonId", "orderDate"]),
        Index("customerId"),
        Index("syncStatus"),
    ],
)
data class OrderEntity(
    @PrimaryKey val id: String,
    /** Client sinh: mã NV + ngày + số thứ tự. Unique để chặn tạo trùng. */
    val orderNo: String,
    val customerId: String,
    val salespersonId: String,
    val visitId: String?,
    val branchId: String,
    val orderDate: String,
    val deliveryDate: String?,
    /** NEW | CONFIRMED | CANCELLED */
    val status: String,

    // ── Tiền: VND, kiểu Long. subTotal - discount = net; net + vat = total ──
    val subTotal: Long,
    val discountAmount: Long,
    val manualDiscount: Long,
    val netAmount: Long,
    val vatAmount: Long,
    val totalAmount: Long,

    val note: String?,
    val reasonCode: String?,

    // ── sync ──
    val syncStatus: SyncStatus = SyncStatus.DRAFT,
    val syncAttempts: Int = 0,
    val lastError: String? = null,
    val sessionId: String? = null,
    val clientCreatedAt: Long,
    val serverAckAt: Long? = null,
)

/**
 * Dòng đơn hàng.
 *
 * [price] và [conversionRate] là SNAPSHOT tại thời điểm đặt, cố ý không join
 * sang price_lists khi hiển thị lại. Nếu join, giá đổi là toàn bộ lịch sử đơn
 * hàng hiển thị sai.
 */
@Entity(
    tableName = "order_details",
    indices = [
        Index("orderId"),
        Index("productId"),
        Index(value = ["orderId", "lineNo"], unique = true),
    ],
)
data class OrderDetailEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val lineNo: Int,
    val productId: String,
    val uomCode: String,
    val qty: Double,
    val conversionRate: Double,
    /** qty × conversionRate — dùng để so điều kiện khuyến mãi và trừ tồn kho. */
    val baseQty: Double,
    val price: Long,
    val grossAmount: Long,
    val discountAmount: Long,
    val netAmount: Long,
    val vatRate: Double,
    val vatAmount: Long,
    val lineAmount: Long,
    val isFreeItem: Boolean,
    val promotionId: String?,
)

/** Vết khuyến mãi đã áp — để đối chiếu khi server kiểm tra lại. */
@Entity(
    tableName = "order_promotions",
    indices = [Index("orderId"), Index("programId")],
)
data class OrderPromotionEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    /** null = khuyến mãi áp trên toàn đơn, không thuộc dòng nào. */
    val orderDetailId: String?,
    val programId: String,
    val breakId: String?,
    /** Số suất được hưởng. */
    val applyTimes: Double,
    val discountAmount: Long,
    val freeQty: Double,
    val isManual: Boolean,
)
