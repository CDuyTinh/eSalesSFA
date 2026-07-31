package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.DailyRevenue
import com.tinhcd.esalessfa.domain.repository.DashboardKpi
import com.tinhcd.esalessfa.domain.repository.MonthStats
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.repository.TodayStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Kho báo cáo giả cho test dashboard.
 *
 * [observeRevenueBetween] chèn đủ ngày trống giống hệt bản thật, vì phần lấp
 * ngày nằm ở tầng data — nếu bản giả trả thiếu ngày thì test biểu đồ sẽ xanh
 * trong khi app thật vẽ sai trục.
 */
class FakeReportRepository(
    private val today: TodayStats = TodayStats(),
    private val month: MonthStats = MonthStats(),
    private val revenueByDate: Map<String, Long> = emptyMap(),
) : ReportRepository {

    var requestedDayOfWeek: Int? = null
        private set

    override fun observeTodayStats(dayOfWeek: Int): Flow<TodayStats> {
        requestedDayOfWeek = dayOfWeek
        return flowOf(today)
    }

    override fun observeMonthStats(): Flow<MonthStats> = flowOf(month)

    override fun observeRevenueBetween(fromDate: String, toDate: String): Flow<List<DailyRevenue>> {
        val from = LocalDate.parse(fromDate)
        val days = ChronoUnit.DAYS.between(from, LocalDate.parse(toDate)).toInt() + 1
        return flowOf(
            (0 until days).map { offset ->
                val date = from.plusDays(offset.toLong()).toString()
                DailyRevenue(date, revenueByDate[date] ?: 0L, 0)
            }
        )
    }

    override fun observeKpi(): Flow<DashboardKpi> = flowOf(DashboardKpi())

    override fun observeDailyRevenue(days: Int): Flow<List<DailyRevenue>> = flowOf(emptyList())

    override fun observeTopProducts(days: Int, limit: Int): Flow<List<RankedItem>> =
        flowOf(emptyList())

    override fun observeTopCustomers(days: Int, limit: Int): Flow<List<RankedItem>> =
        flowOf(emptyList())

    override fun observeOrders(
        fromDate: String,
        toDate: String,
        customerId: String?,
    ): Flow<List<OrderSummary>> = flowOf(emptyList())
}
