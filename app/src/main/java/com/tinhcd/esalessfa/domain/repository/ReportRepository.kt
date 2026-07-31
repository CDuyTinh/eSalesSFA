package com.tinhcd.esalessfa.domain.repository

import kotlinx.coroutines.flow.Flow

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

interface ReportRepository {

    fun observeKpi(): Flow<DashboardKpi>

    /** Số liệu hôm nay; [dayOfWeek] theo Calendar.DAY_OF_WEEK để lấy đúng tuyến. */
    fun observeTodayStats(dayOfWeek: Int): Flow<TodayStats>

    fun observeMonthStats(): Flow<MonthStats>

    /** Doanh số từng ngày trong [days] ngày gần nhất, để vẽ biểu đồ. */
    fun observeDailyRevenue(days: Int = 14): Flow<List<DailyRevenue>>

    /** Doanh số từng ngày trong một khoảng đóng, đã chèn ngày trống bằng 0. */
    fun observeRevenueBetween(fromDate: String, toDate: String): Flow<List<DailyRevenue>>

    fun observeTopProducts(days: Int = 30, limit: Int = 5): Flow<List<RankedItem>>

    fun observeTopCustomers(days: Int = 30, limit: Int = 5): Flow<List<RankedItem>>

    fun observeOrders(
        fromDate: String,
        toDate: String,
        customerId: String? = null,
    ): Flow<List<OrderSummary>>
}
