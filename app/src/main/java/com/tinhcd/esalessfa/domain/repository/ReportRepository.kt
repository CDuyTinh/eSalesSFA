package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.report.CustomerReportItem
import com.tinhcd.esalessfa.domain.model.report.DailyRevenue
import com.tinhcd.esalessfa.domain.model.report.DashboardKpi
import com.tinhcd.esalessfa.domain.model.report.MonthStats
import com.tinhcd.esalessfa.domain.model.report.OrderDetail
import com.tinhcd.esalessfa.domain.model.report.OrderSummary
import com.tinhcd.esalessfa.domain.model.report.ProductReportItem
import com.tinhcd.esalessfa.domain.model.report.RankedItem
import com.tinhcd.esalessfa.domain.model.report.TodayStats
import kotlinx.coroutines.flow.Flow

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

    /** Doanh số theo sản phẩm trong một khoảng đóng, đã xếp giảm dần theo tiền. */
    fun observeProductReport(fromDate: String, toDate: String): Flow<List<ProductReportItem>>

    /** Doanh số theo khách hàng trong một khoảng đóng, đã xếp giảm dần theo tiền. */
    fun observeCustomerReport(fromDate: String, toDate: String): Flow<List<CustomerReportItem>>

    /** Một đơn kèm đủ dòng hàng; null khi không còn đơn nào mang id đó. */
    fun observeOrderDetail(orderId: String): Flow<OrderDetail?>
}
