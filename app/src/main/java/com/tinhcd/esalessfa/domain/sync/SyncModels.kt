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
    const val SURVEY_TYPES = "survey_types"
    const val SURVEY_QUESTION_GROUPS = "survey_question_groups"
    const val SURVEY_QUESTIONS = "survey_questions"
    const val SURVEY_QUESTION_OPTIONS = "survey_question_options"
    const val ORDERS = "orders"
    const val ORDER_DETAILS = "order_details"
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

    /**
     * Bỏ qua vì điều kiện nghiệp vụ chưa cho phép, không phải lỗi.
     *
     * Khác Failed ở chỗ worker coi đây là thành công: chuỗi gửi-lên-rồi-tải-xuống
     * vẫn chạy tiếp phần tải xuống, và WorkManager không ghi nhận một lần thất bại
     * giả.
     */
    data class Skipped(val reason: String) : SyncProgress
}

/**
 * Trạng thái một lượt sync đang chạy, nhìn từ ngoài vào.
 *
 * Khác [SyncProgress] ở góc nhìn: SyncProgress là các mốc worker phát ra trong
 * lúc chạy, còn SyncRun là ảnh chụp hiện tại của cả lượt để màn hình vẽ lại sau
 * khi bị huỷ và dựng lại. Cố ý không mang kiểu nào của WorkManager để tầng
 * presentation không phải biết công việc chạy bằng gì.
 */
data class SyncRun(
    val status: SyncRunStatus = SyncRunStatus.IDLE,
    val page: Int = 0,
    val totalRows: Int = 0,
    val currentTable: String? = null,
    /** Lời báo lỗi từ server, nếu có. Chữ hiển thị cho người dùng do UI quyết định. */
    val errorMessage: String? = null,
)

enum class SyncRunStatus {
    /** Chưa có lượt nào, hoặc lượt cũ đã bị xoá dấu vết. */
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** Kết quả một lượt sync, dùng cho worker quyết định retry hay bỏ. */
data class SyncOutcome(
    val totalRows: Int,
    val pages: Int,
    val durationMs: Long,
)
