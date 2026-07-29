package com.tinhcd.esalessfa.core.database.entity.master

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Khách hàng.
 *
 * [nameSearch] là tên đã bỏ dấu và lowercase, do server sinh sẵn. SQLite không có
 * unaccent như Postgres, nên nếu không có cột này thì gõ "an khang" sẽ không tìm
 * ra "An Khang" — lỗi rất khó chịu với 200+ khách hàng.
 */
@Entity(
    tableName = "customers",
    indices = [
        Index("code", unique = true),
        Index("salespersonId"),
        Index("channelId"),
        Index("nameSearch"),
    ],
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val nameSearch: String?,
    val phone: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val channelId: String?,
    val priceGroupId: String,
    val branchId: String,
    val salespersonId: String?,
    val creditLimit: Long,
    val debtAmount: Long,
    val imageUrl: String?,
    val isActive: Boolean,
)

/**
 * Tuyến viếng thăm: nhân viên × thứ trong tuần × tần suất.
 *
 * [weekPattern] = ALL | ODD | EVEN — tuyến tuần chẵn/lẻ, dùng khi khách hàng chỉ
 * cần ghé hai tuần một lần.
 */
@Entity(
    tableName = "sales_routes",
    indices = [Index(value = ["salespersonId", "dayOfWeek"])],
)
data class SalesRouteEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val salespersonId: String,
    /** 1 = Chủ nhật ... 7 = Thứ bảy (khớp Calendar.DAY_OF_WEEK). */
    val dayOfWeek: Int,
    val weekPattern: String,
)

@Entity(
    tableName = "sales_route_details",
    indices = [
        Index("routeId"),
        Index("customerId"),
        Index(value = ["routeId", "customerId"], unique = true),
    ],
)
data class SalesRouteDetailEntity(
    @PrimaryKey val id: String,
    val routeId: String,
    val customerId: String,
    val sortOrder: Int,
)
