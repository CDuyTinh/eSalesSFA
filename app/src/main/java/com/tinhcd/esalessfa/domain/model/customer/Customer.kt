package com.tinhcd.esalessfa.domain.model.customer

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint

/**
 * Khách hàng ở tầng domain.
 *
 * Tách khỏi CustomerEntity để tầng UI không phụ thuộc Room: đổi cấu trúc bảng
 * hay chuyển sang Compose đều không đụng tới model này.
 */
data class Customer(
    val id: String,
    val code: String,
    val name: String,
    val phone: String?,
    val address: String?,
    val location: GeoPoint?,
    val channelId: String?,
    /** Tên kênh để hiện trên thẻ; null khi khách chưa gán kênh. */
    val channelName: String?,
    val priceGroupId: String,
    val creditLimit: Long,
    val debtAmount: Long,
    val imageUrl: String?,
) {
    /** Có toạ độ mới xác thực được check-in theo bán kính. */
    val canValidateCheckIn: Boolean get() = location != null

    val availableCredit: Long get() = (creditLimit - debtAmount).coerceAtLeast(0)
}

/** Trạng thái viếng thăm trong ngày, hiển thị trên danh sách tuyến. */
enum class VisitState { NOT_VISITED, IN_PROGRESS, DONE }

data class RouteCustomer(
    val customer: Customer,
    val sortOrder: Int,
    val visitState: VisitState,
)
