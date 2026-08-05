package com.tinhcd.esalessfa.domain.model.sync

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

    /**
     * Toàn bộ bảng client biết cách ghi — mẫu số của thanh tiến trình.
     *
     * Server có thể thêm bảng mới mà client chưa biết; những bảng đó bị bỏ qua
     * lúc ghi nên cũng không được tính vào tiến trình, nếu không thanh chạy mãi
     * không tới đích.
     */
    val ALL: List<String> = listOf(
        APP_CONFIGS, UOMS, BRANCHES, CHANNELS, PRICE_GROUPS, REASON_CODES, SALESPERSONS,
        CUSTOMERS, SALES_ROUTES, SALES_ROUTE_DETAILS,
        PRODUCT_CATEGORIES, PRODUCTS, PRODUCT_UOMS,
        PRICE_LISTS, PROMOTION_PROGRAMS, PROMOTION_BREAKS, PROMOTION_ITEMS,
        SURVEY_TYPES, SURVEY_QUESTION_GROUPS, SURVEY_QUESTIONS, SURVEY_QUESTION_OPTIONS,
        ORDERS, ORDER_DETAILS,
    )
}

/**
 * Nhóm bảng để hiển thị, không phải để xử lý.
 *
 * Người dùng không cần biết "product_uoms" hay "promotion_breaks" là gì; họ cần
 * biết đã xong phần nào của công việc. Gom 23 bảng kỹ thuật thành 6 nhóm nghiệp
 * vụ đọc được.
 *
 * ⚠️ Thứ tự các nhóm ở đây chỉ để ĐỌC, không phải thứ tự tải. Server tải song
 * song toàn bộ bảng trong mỗi trang (Promise.all trong sync-download), nên các
 * nhóm xong theo khối lượng dữ liệu chứ không theo thứ tự nào cả — nhóm nhỏ
 * xong ngay trang đầu, nhóm nặng chạy thêm vài trang. Vì vậy màn hình chỉ được
 * nói "xong" hay "đang tải", tuyệt đối không nói "chờ tới lượt".
 */
enum class SyncGroup(val tables: List<String>) {
    CATALOG(
        listOf(
            SyncTables.APP_CONFIGS, SyncTables.UOMS, SyncTables.BRANCHES,
            SyncTables.CHANNELS, SyncTables.PRICE_GROUPS, SyncTables.REASON_CODES,
            SyncTables.SALESPERSONS,
        )
    ),
    CUSTOMER(
        listOf(
            SyncTables.CUSTOMERS, SyncTables.SALES_ROUTES, SyncTables.SALES_ROUTE_DETAILS,
        )
    ),
    PRODUCT(
        listOf(
            SyncTables.PRODUCT_CATEGORIES, SyncTables.PRODUCTS, SyncTables.PRODUCT_UOMS,
        )
    ),
    PRICING(
        listOf(
            SyncTables.PRICE_LISTS, SyncTables.PROMOTION_PROGRAMS,
            SyncTables.PROMOTION_BREAKS, SyncTables.PROMOTION_ITEMS,
        )
    ),
    SURVEY(
        listOf(
            SyncTables.SURVEY_TYPES, SyncTables.SURVEY_QUESTION_GROUPS,
            SyncTables.SURVEY_QUESTIONS, SyncTables.SURVEY_QUESTION_OPTIONS,
        )
    ),
    ORDER_HISTORY(listOf(SyncTables.ORDERS, SyncTables.ORDER_DETAILS));

    /** Nhóm coi như xong khi mọi bảng trong nhóm đã tải xong. */
    fun isDone(doneTables: Set<String>): Boolean = doneTables.containsAll(tables)
}

/** Hai bước của một lượt đồng bộ đầy đủ, để màn hình nói rõ đang ở bước nào. */
enum class SyncPhase { UPLOAD, DOWNLOAD }

/**
 * Tiến trình sync để UI hiển thị.
 *
 * Không dùng phần trăm cố định: số trang chỉ biết được khi server trả has_more,
 * nên hiển thị theo trang và số dòng đã ghi là trung thực hơn thanh progress giả.
 */
sealed interface SyncProgress {

    data object Started : SyncProgress

    /**
     * [doneTables] là các bảng đã tải xong: hoặc server không còn thay đổi nào
     * để trả, hoặc trang vừa rồi trả về ít hơn một trang đầy. Đây là thứ duy
     * nhất đo được tiến trình — tổng số dòng thì tới cuối mới biết.
     */
    data class Downloading(
        val page: Int,
        val rowsThisPage: Int,
        val totalRows: Int,
        val currentTable: String?,
        val doneTables: Set<String>,
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
    val phase: SyncPhase = SyncPhase.DOWNLOAD,
    val page: Int = 0,
    val totalRows: Int = 0,
    val currentTable: String? = null,
    val doneTables: Set<String> = emptySet(),
    /** Lời báo lỗi từ server, nếu có. Chữ hiển thị cho người dùng do UI quyết định. */
    val errorMessage: String? = null,
) {
    /** Phần trăm để vẽ thanh tiến trình; bước gửi lên coi như phần đầu của lượt. */
    val percent: Int
        get() = when {
            status == SyncRunStatus.SUCCEEDED -> 100
            SyncTables.ALL.isEmpty() -> 0
            else -> doneTables.size * 100 / SyncTables.ALL.size
        }
}

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
