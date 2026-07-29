package com.tinhcd.esalessfa.domain.sync

/** Tên bảng đồng bộ. Dùng hằng số thay chuỗi rời rạc để tránh gõ sai. */
object SyncTables {
    const val APP_CONFIGS = "app_configs"
    const val UOMS = "uoms"
    const val BRANCHES = "branches"
    const val CHANNELS = "channels"
    const val PRICE_GROUPS = "price_groups"
    const val REASON_CODES = "reason_codes"
    const val SALESPERSONS = "salespersons"
    const val CUSTOMERS = "customers"
    const val SALES_ROUTES = "sales_routes"
    const val SALES_ROUTE_DETAILS = "sales_route_details"
    const val PRODUCT_CATEGORIES = "product_categories"
    const val PRODUCTS = "products"
    const val PRODUCT_UOMS = "product_uoms"
    const val PRICE_LISTS = "price_lists"
    const val PROMOTION_PROGRAMS = "promotion_programs"
    const val PROMOTION_BREAKS = "promotion_breaks"
    const val PROMOTION_ITEMS = "promotion_items"
}

/**
 * Tiến trình sync để UI hiển thị.
 *
 * Không dùng phần trăm cố định: số trang chỉ biết được khi server trả has_more,
 * nên hiển thị theo trang và số dòng đã ghi là trung thực hơn thanh progress giả.
 */
sealed interface SyncProgress {

    data object Started : SyncProgress

    data class Downloading(
        val page: Int,
        val rowsThisPage: Int,
        val totalRows: Int,
        val currentTable: String?,
    ) : SyncProgress

    data class Uploading(
        val sent: Int,
        val total: Int,
    ) : SyncProgress

    data class Completed(
        val totalRows: Int,
        val pages: Int,
        val durationMs: Long,
    ) : SyncProgress

    data class Failed(
        val message: String,
        val isRetryable: Boolean,
    ) : SyncProgress
}

/** Kết quả một lượt sync, dùng cho worker quyết định retry hay bỏ. */
data class SyncOutcome(
    val totalRows: Int,
    val pages: Int,
    val durationMs: Long,
)
