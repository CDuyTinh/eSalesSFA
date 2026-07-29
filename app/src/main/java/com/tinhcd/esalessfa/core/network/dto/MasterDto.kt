package com.tinhcd.esalessfa.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// DTO của dữ liệu master trả về từ sync-download.
//
// Tách hẳn khỏi Room Entity: server đổi tên cột thì chỉ sửa @SerialName ở đây,
// không kéo theo migration Room. Ngược lại, đổi cấu trúc bảng local cũng không
// làm hỏng việc đọc response.
//
// Mọi DTO đều có rowVersion — client dùng nó để cập nhật mốc trong sync_state.
// =============================================================================

@Serializable
data class AppConfigDto(
    val code: String,
    val value: String,
    @SerialName("data_type") val dataType: String,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class UomDto(
    val code: String,
    val name: String,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class BranchDto(
    val id: String,
    val code: String,
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class ChannelDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class PriceGroupDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class ReasonCodeDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("apply_for") val applyFor: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SalespersonDto(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val code: String,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("branch_id") val branchId: String? = null,
    val role: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class CustomerDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("name_search") val nameSearch: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("channel_id") val channelId: String? = null,
    @SerialName("price_group_id") val priceGroupId: String,
    @SerialName("branch_id") val branchId: String,
    @SerialName("salesperson_id") val salespersonId: String? = null,
    @SerialName("credit_limit") val creditLimit: Long = 0,
    @SerialName("debt_amount") val debtAmount: Long = 0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SalesRouteDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("salesperson_id") val salespersonId: String,
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("week_pattern") val weekPattern: String = "ALL",
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SalesRouteDetailDto(
    val id: String,
    @SerialName("route_id") val routeId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class ProductCategoryDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class ProductDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("name_search") val nameSearch: String? = null,
    val barcode: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("base_uom") val baseUom: String,
    @SerialName("vat_rate") val vatRate: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_track_stock") val isTrackStock: Boolean = true,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class ProductUomDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("uom_code") val uomCode: String,
    @SerialName("conversion_rate") val conversionRate: Double,
    @SerialName("is_default_sale") val isDefaultSale: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class PriceListDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    @SerialName("price_group_id") val priceGroupId: String,
    @SerialName("uom_code") val uomCode: String,
    val price: Long,
    @SerialName("from_date") val fromDate: String,
    @SerialName("to_date") val toDate: String,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class PromotionProgramDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("promo_type") val promoType: String,
    @SerialName("apply_level") val applyLevel: String,
    @SerialName("discount_kind") val discountKind: String,
    @SerialName("is_auto_apply") val isAutoApply: Boolean = true,
    @SerialName("is_multi_level") val isMultiLevel: Boolean = false,
    val priority: Int = 0,
    @SerialName("from_date") val fromDate: String,
    @SerialName("to_date") val toDate: String,
    @SerialName("budget_amount") val budgetAmount: Long? = null,
    @SerialName("used_amount") val usedAmount: Long = 0,
    @SerialName("exclude_program_codes") val excludeProgramCodes: String? = null,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class PromotionBreakDto(
    val id: String,
    @SerialName("program_id") val programId: String,
    @SerialName("break_level") val breakLevel: Int,
    @SerialName("min_qty") val minQty: Double? = null,
    @SerialName("min_amount") val minAmount: Long? = null,
    @SerialName("discount_pct") val discountPct: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Long = 0,
    @SerialName("free_qty") val freeQty: Double = 0.0,
    @SerialName("max_apply_times") val maxApplyTimes: Int? = null,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class PromotionItemDto(
    val id: String,
    @SerialName("program_id") val programId: String,
    @SerialName("break_id") val breakId: String? = null,
    @SerialName("product_id") val productId: String,
    @SerialName("item_role") val itemRole: String,
    @SerialName("bundle_group") val bundleGroup: String? = null,
    @SerialName("required_qty") val requiredQty: Double = 0.0,
    @SerialName("uom_code") val uomCode: String? = null,
    @SerialName("free_stock_qty") val freeStockQty: Double? = null,
    @SerialName("row_version") val rowVersion: Long,
)
