package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.database.dao.ReportDao
import com.tinhcd.esalessfa.domain.repository.DailyRevenue
import com.tinhcd.esalessfa.domain.repository.DashboardKpi
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val dao: ReportDao,
) : ReportRepository {

    override fun observeKpi(): Flow<DashboardKpi> {
        val today = LocalDate.now().format(ISO)
        val month = today.substring(0, 7)

        // Gộp theo hai nhóm vì combine chỉ giữ nguyên kiểu tới 5 luồng.
        val todayPart = combine(
            dao.observeRevenueOn(today),
            dao.observeOrderCountOn(today),
            dao.observeVisitedCountOn(today),
        ) { revenue, orders, visited -> Triple(revenue, orders, visited) }

        val monthPart = combine(
            dao.observeRevenueInMonth(month),
            dao.observeOrderCountInMonth(month),
        ) { revenue, orders -> revenue to orders }

        return combine(todayPart, monthPart) { (revenue, orders, visited), (mRevenue, mOrders) ->
            DashboardKpi(
                todayRevenue = revenue,
                todayOrderCount = orders,
                todayVisitedCount = visited,
                monthRevenue = mRevenue,
                monthOrderCount = mOrders,
            )
        }
    }

    /**
     * Chèn ngày không có đơn với giá trị 0.
     *
     * Truy vấn GROUP BY chỉ trả về ngày CÓ đơn, nên biểu đồ sẽ bỏ qua ngày nghỉ
     * và làm trục thời gian bị co lại — nhìn như bán đều mọi ngày.
     */
    override fun observeDailyRevenue(days: Int): Flow<List<DailyRevenue>> {
        val from = LocalDate.now().minusDays((days - 1).toLong())
        return dao.observeDailyRevenue(from.format(ISO)).map { rows ->
            val byDate = rows.associateBy { it.orderDate }
            (0 until days).map { offset ->
                val date = from.plusDays(offset.toLong()).format(ISO)
                val row = byDate[date]
                DailyRevenue(date, row?.amount ?: 0L, row?.orderCount ?: 0)
            }
        }
    }

    override fun observeTopProducts(days: Int, limit: Int): Flow<List<RankedItem>> =
        dao.observeTopProducts(fromDate(days), limit)
            .map { rows -> rows.map { RankedItem(it.id, it.name, it.amount, it.qty) } }

    override fun observeTopCustomers(days: Int, limit: Int): Flow<List<RankedItem>> =
        dao.observeTopCustomers(fromDate(days), limit)
            .map { rows -> rows.map { RankedItem(it.id, it.name, it.amount, it.qty) } }

    override fun observeOrders(
        fromDate: String,
        toDate: String,
        customerId: String?,
    ): Flow<List<OrderSummary>> =
        dao.observeOrderReport(fromDate, toDate, customerId).map { rows ->
            rows.map { row ->
                OrderSummary(
                    id = row.id,
                    orderNo = row.orderNo,
                    orderDate = row.orderDate,
                    customerName = row.customerName,
                    totalAmount = row.totalAmount,
                    status = row.status,
                    isSynced = row.syncStatus == "SYNCED",
                    lineCount = row.lineCount,
                )
            }
        }

    private fun fromDate(days: Int): String =
        LocalDate.now().minusDays(days.toLong()).format(ISO)

    private companion object {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
