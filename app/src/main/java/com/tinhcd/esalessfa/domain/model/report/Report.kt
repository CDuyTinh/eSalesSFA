package com.tinhcd.esalessfa.domain.model.report

data class DailyRevenue(val date: String, val amount: Long, val orderCount: Int)

data class RankedItem(val id: String, val name: String, val amount: Long, val qty: Double)

data class OrderSummary(
    val id: String,
    val orderNo: String,
    val orderDate: String,
    val customerName: String,
    val totalAmount: Long,
    val status: String,
    val isSynced: Boolean,
    val lineCount: Int,
)

/** Một dòng hàng trong đơn. [isFreeItem] là hàng tặng nên không tính tiền. */
data class OrderDetailLine(
    val id: String,
    val productCode: String,
    val productName: String,
    val uomCode: String,
    val qty: Double,
    val price: Long,
    val discountAmount: Long,
    val lineAmount: Long,
    val isFreeItem: Boolean,
)

/**
 * Toàn bộ một đơn hàng để xem lại: phần đầu và các dòng hàng.
 *
 * [discountAmount] gộp chiết khấu do khuyến mãi và phần nhân viên tự nhập —
 * người xem chỉ quan tâm đơn được giảm bao nhiêu, không quan tâm giảm từ đâu.
 */
data class OrderDetail(
    val orderNo: String,
    val orderDate: String,
    val deliveryDate: String?,
    val status: String,
    val isSynced: Boolean,
    val customerCode: String,
    val customerName: String,
    val customerAddress: String?,
    val subTotal: Long,
    val discountAmount: Long,
    val vatAmount: Long,
    val totalAmount: Long,
    val note: String?,
    val lines: List<OrderDetailLine>,
)

/** Một sản phẩm trong báo cáo kỳ: [qty] tính theo đơn vị nhỏ nhất của sản phẩm. */
data class ProductReportItem(
    val id: String,
    val code: String,
    val name: String,
    val amount: Long,
    val qty: Double,
    val orderCount: Int,
)

/** Một khách hàng trong báo cáo kỳ. */
data class CustomerReportItem(
    val id: String,
    val code: String,
    val name: String,
    val amount: Long,
    val orderCount: Int,
    val skuCount: Int,
)

data class DashboardKpi(
    val todayRevenue: Long = 0,
    val todayOrderCount: Int = 0,
    val todayVisitedCount: Int = 0,
    val monthRevenue: Long = 0,
    val monthOrderCount: Int = 0,
) {
    /** Giá trị đơn trung bình trong tháng — chỉ số hay dùng để đánh giá nhân viên. */
    val avgOrderValue: Long
        get() = if (monthOrderCount == 0) 0 else monthRevenue / monthOrderCount
}

/**
 * Số liệu thô của ngày hôm nay.
 *
 * [routePlanCount] là số khách phải ghé theo tuyến — chỉ tiêu của cả đơn hàng
 * lẫn viếng thăm, nên hai ô trên dashboard đều so với con số này.
 */
data class TodayStats(
    val revenue: Long = 0,
    val orderCount: Int = 0,
    val visitedCount: Int = 0,
    val routePlanCount: Int = 0,
    /** Số cặp (đơn, mặt hàng) trong ngày; chia cho số đơn ra SKU/Đơn hàng. */
    val skuLines: Int = 0,
)

/** Số liệu thô của tháng đang chạy, chưa quy ra phần trăm. */
data class MonthStats(
    val revenue: Long = 0,
    val orderCount: Int = 0,
    val visitedCount: Int = 0,
    /** Số lượt ghé có phát sinh đơn. */
    val productiveVisitCount: Int = 0,
    val skuLines: Int = 0,
    /** Số khách khác nhau đã mua trong tháng. */
    val buyingCustomerCount: Int = 0,
    /** Tổng số khách được phân tuyến. */
    val routeCustomerCount: Int = 0,
    /** Kế hoạch ghé theo từng thứ (1 = Chủ nhật ... 7 = Thứ bảy). */
    val routePlanByWeekday: Map<Int, Int> = emptyMap(),
)
