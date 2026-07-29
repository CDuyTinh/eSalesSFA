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

interface ReportRepository {

    fun observeKpi(): Flow<DashboardKpi>

    /** Doanh số từng ngày trong [days] ngày gần nhất, để vẽ biểu đồ. */
    fun observeDailyRevenue(days: Int = 14): Flow<List<DailyRevenue>>

    fun observeTopProducts(days: Int = 30, limit: Int = 5): Flow<List<RankedItem>>

    fun observeTopCustomers(days: Int = 30, limit: Int = 5): Flow<List<RankedItem>>

    fun observeOrders(
        fromDate: String,
        toDate: String,
        customerId: String? = null,
    ): Flow<List<OrderSummary>>
}
