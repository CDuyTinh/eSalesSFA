package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.MonthStats
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.repository.TodayStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Một ô trong thẻ "Tổng kết ngày": số đạt được so với chỉ tiêu. */
data class DayMetric(
    val actual: Double,
    /** null nghĩa là chỉ số này không có chỉ tiêu, chỉ hiển thị con số. */
    val target: Int?,
)

data class DaySummary(
    val revenue: Long = 0,
    val orders: DayMetric = DayMetric(0.0, 0),
    val skuPerOrder: DayMetric = DayMetric(0.0, null),
    val visits: DayMetric = DayMetric(0.0, 0),
)

/** Loại KPI tháng — UI dùng để chọn màu và chữ hiển thị. */
enum class MonthKpiType { VISIT_COVERAGE, PRODUCTIVE_CALL, CUSTOMER_COVERAGE, SKU_PER_ORDER }

/**
 * Một dòng KPI tháng.
 *
 * [ratio] nằm trong khoảng 0..1 và là thứ vòng tròn tiến độ vẽ ra; [actual] và
 * [target] giữ nguyên con số thật để hiện dạng "12/40".
 */
data class MonthKpi(
    val type: MonthKpiType,
    val actual: Double,
    val target: Double,
) {
    val ratio: Double get() = if (target <= 0.0) 0.0 else (actual / target).coerceIn(0.0, 1.0)
    val percent: Int get() = (ratio * 100).toInt()
}

data class DashboardSnapshot(
    val day: DaySummary = DaySummary(),
    val monthRevenue: Long = 0,
    val monthKpis: List<MonthKpi> = emptyList(),
)

/**
 * Tổng hợp dashboard: số của hôm nay và các chỉ số KPI trong tháng.
 *
 * Mọi chỉ tiêu ở đây đều suy ra từ dữ liệu đã tải về, KHÔNG bịa: chỉ tiêu ngày
 * là số khách phải ghé theo tuyến hôm nay, còn chỉ tiêu ghé cả tháng là kế
 * hoạch từng thứ nhân với số lần thứ đó đã trôi qua tính đến hôm nay. Nhân tới
 * hôm nay chứ không nhân cả tháng, nếu không thì ngày mùng 2 nhìn nào cũng thấy
 * mình đạt 7%.
 */
class ObserveDashboardUseCase @Inject constructor(
    private val repository: ReportRepository,
) {

    operator fun invoke(today: LocalDate = LocalDate.now()): Flow<DashboardSnapshot> {
        // Calendar.DAY_OF_WEEK: 1 = Chủ nhật ... 7 = Thứ bảy, còn DayOfWeek của
        // java.time là 1 = Thứ hai ... 7 = Chủ nhật. Tuyến lưu theo kiểu Calendar.
        val calendarDayOfWeek = today.dayOfWeek.value % 7 + 1

        return combine(
            repository.observeTodayStats(calendarDayOfWeek),
            repository.observeMonthStats(),
        ) { day, month ->
            DashboardSnapshot(
                day = day.toSummary(),
                monthRevenue = month.revenue,
                monthKpis = month.toKpis(today),
            )
        }
    }

    private fun TodayStats.toSummary() = DaySummary(
        revenue = revenue,
        orders = DayMetric(orderCount.toDouble(), routePlanCount),
        // SKU/Đơn hàng không có chỉ tiêu: nó là chỉ số chất lượng đơn, cao hay
        // thấp còn tuỳ ngành hàng nên áp một con số cứng sẽ vô nghĩa.
        skuPerOrder = DayMetric(
            actual = if (orderCount == 0) 0.0 else skuLines.toDouble() / orderCount,
            target = null,
        ),
        visits = DayMetric(visitedCount.toDouble(), routePlanCount),
    )

    private fun MonthStats.toKpis(today: LocalDate): List<MonthKpi> = listOf(
        MonthKpi(
            type = MonthKpiType.VISIT_COVERAGE,
            actual = visitedCount.toDouble(),
            target = plannedVisitsUntil(today).toDouble(),
        ),
        // PC đếm LƯỢT GHÉ có đơn trên tổng lượt ghé, không phải số đơn trên số
        // ghé: ghé một lần viết hai đơn vẫn là một lượt hiệu quả, tính theo đơn
        // sẽ đẩy chỉ số vượt 100% và không còn ý nghĩa "ghé đâu bán đó".
        MonthKpi(
            type = MonthKpiType.PRODUCTIVE_CALL,
            actual = productiveVisitCount.toDouble(),
            target = visitedCount.toDouble(),
        ),
        MonthKpi(
            type = MonthKpiType.CUSTOMER_COVERAGE,
            actual = buyingCustomerCount.toDouble(),
            target = routeCustomerCount.toDouble(),
        ),
        MonthKpi(
            type = MonthKpiType.SKU_PER_ORDER,
            actual = if (orderCount == 0) 0.0 else skuLines.toDouble() / orderCount,
            target = SKU_PER_ORDER_GOAL,
        ),
    )

    /**
     * Số lượt ghé đáng lẽ phải làm từ đầu tháng tới hôm nay.
     *
     * Đếm từng thứ đã trôi qua rồi nhân với số khách của tuyến thứ đó. Hôm nay
     * tính vào vì nhân viên còn cả ngày để đi.
     */
    private fun MonthStats.plannedVisitsUntil(today: LocalDate): Int {
        val firstDay = today.withDayOfMonth(1)
        val elapsedDays = ChronoUnit.DAYS.between(firstDay, today).toInt() + 1

        return (0 until elapsedDays).sumOf { offset ->
            val date = firstDay.plusDays(offset.toLong())
            val calendarDayOfWeek = date.dayOfWeek.value % 7 + 1
            routePlanByWeekday[calendarDayOfWeek] ?: 0
        }
    }

    private companion object {
        /** Mốc "đơn hàng đầy đủ" dùng chung khi công ty chưa giao chỉ tiêu SKU. */
        const val SKU_PER_ORDER_GOAL = 5.0
    }
}
