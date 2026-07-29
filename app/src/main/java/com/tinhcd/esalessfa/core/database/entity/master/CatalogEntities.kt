package com.tinhcd.esalessfa.core.database.entity.master

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// =============================================================================
// Danh mục & cấu hình — tải từ server, client chỉ đọc.
//
// Không mang cột row_version: mốc version lưu tập trung ở bảng sync_state, mỗi
// bảng một dòng. Lặp lại version trên từng bản ghi vừa thừa vừa dễ lệch.
// =============================================================================

/** Tham số vận hành do server điều khiển: bán kính check-in, ngưỡng GPS... */
@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val code: String,
    val value: String,
    val dataType: String,
)

/** Đơn vị tính: Lẻ / Lốc / Thùng. */
@Entity(tableName = "uoms")
data class UomEntity(
    @PrimaryKey val code: String,
    val name: String,
)

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
)

@Entity(tableName = "price_groups")
data class PriceGroupEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
)

/**
 * Lý do bắt buộc chọn khi thao tác vượt quy định.
 * [applyFor] quyết định dialog nào hiện danh sách nào.
 */
@Entity(
    tableName = "reason_codes",
    indices = [Index("applyFor")],
)
data class ReasonCodeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val applyFor: String,
    val sortOrder: Int,
)

@Entity(tableName = "salespersons")
data class SalespersonEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val code: String,
    val fullName: String,
    val phone: String?,
    val email: String?,
    val branchId: String?,
    val role: String,
    val isActive: Boolean,
)
