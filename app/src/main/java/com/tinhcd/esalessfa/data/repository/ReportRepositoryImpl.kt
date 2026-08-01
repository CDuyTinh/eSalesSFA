package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.database.dao.ReportDao
import com.tinhcd.esalessfa.domain.repository.CustomerReportItem
import com.tinhcd.esalessfa.domain.repository.DailyRevenue
import com.tinhcd.esalessfa.domain.repository.DashboardKpi
import com.tinhcd.esalessfa.domain.repository.MonthStats
import com.tinhcd.esalessfa.domain.repository.OrderDetail
import com.tinhcd.esalessfa.domain.repository.OrderDetailLine
import com.tinhcd.esalessfa.domain.repository.ProductReportItem
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.repository.TodayStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

    override fun observeTodayStats(dayOfWeek: Int): Flow<TodayStats> {
        val today = LocalDate.now().format(ISO)

        val sold = combine(
            dao.observeRevenueOn(today),
            dao.observeOrderCountOn(today),
            dao.observeSkuLinesOn(today),
        ) { revenue, orders, skuLines -> Triple(revenue, orders, skuLines) }

        val visits = combine(
            dao.observeVisitedCountOn(today),
            dao.observeRoutePlanOn(dayOfWeek),
        ) { visited, plan -> visited to plan }

        return combine(sold, visits) { (revenue, orders, skuLines), (visited, plan) ->
            TodayStats(
                revenue = revenue,
                orderCount = orders,
                visitedCount = visited,
                routePlanCount = plan,
                skuLines = skuLines,
            )
        }
    }

    override fun observeMonthStats(): Flow<MonthStats> {
        val month = LocalDate.now().format(ISO).substring(0, 7)

        val sold = combine(
            dao.observeRevenueInMonth(month),
            dao.observeOrderCountInMonth(month),
            dao.observeSkuLinesInMonth(month),
            dao.observeBuyingCustomerCountInMonth(month),
        ) { revenue, orders, skuLines, buyers ->
            MonthStats(
                revenue = revenue,
                orderCount = orders,
                skuLines = skuLines,
                buyingCustomerCount = buyers,
            )
        }

        val route = combine(
            dao.observeVisitedCountInMonth(month),
            dao.observeProductiveVisitCountInMonth(month),
            dao.observeRouteCustomerCount(),
            dao.observeRoutePlanByWeekday(),
        ) { visited, productive, routeCustomers, plan ->
            MonthStats(
                visitedCount = visited,
                productiveVisitCount = productive,
                routeCustomerCount = routeCustomers,
                routePlanByWeekday = plan.associate { it.dayOfWeek to it.customerCount },
            )
        }

        return combine(sold, route) { stats, routeStats ->
            stats.copy(
                visitedCount = routeStats.visitedCount,
                productiveVisitCount = routeStats.productiveVisitCount,
                routeCustomerCount = routeStats.routeCustomerCount,
                routePlanByWeekday = routeStats.routePlanByWeekday,
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

    override fun observeRevenueBetween(
        fromDate: String,
        toDate: String,
    ): Flow<List<DailyRevenue>> {
        val from = LocalDate.parse(fromDate)
        val to = LocalDate.parse(toDate)
        val days = ChronoUnit.DAYS.between(from, to).toInt() + 1

        return dao.observeRevenueBetween(fromDate, toDate).map { rows ->
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
                    isSynced = row.syncStatus == SYNCED,
                    lineCount = row.lineCount,
                )
            }
        }

    override fun observeProductReport(
        fromDate: String,
        toDate: String,
    ): Flow<List<ProductReportItem>> =
        dao.observeProductReport(fromDate, toDate).map { rows ->
            rows.map {
                ProductReportItem(
                    id = it.id,
                    code = it.code,
                    name = it.name,
                    amount = it.amount,
                    qty = it.qty,
                    orderCount = it.orderCount,
                )
            }
        }

    override fun observeCustomerReport(
        fromDate: String,
        toDate: String,
    ): Flow<List<CustomerReportItem>> =
        dao.observeCustomerReport(fromDate, toDate).map { rows ->
            rows.map {
                CustomerReportItem(
                    id = it.id,
                    code = it.code,
                    name = it.name,
                    amount = it.amount,
                    orderCount = it.orderCount,
                    skuCount = it.skuCount,
                )
            }
        }

    override fun observeOrderDetail(orderId: String): Flow<OrderDetail?> = combine(
        dao.observeOrderHeader(orderId),
        dao.observeOrderLines(orderId),
    ) { header, lines ->
        header ?: return@combine null

        OrderDetail(
            orderNo = header.orderNo,
            orderDate = header.orderDate,
            deliveryDate = header.deliveryDate,
            status = header.status,
            isSynced = header.syncStatus == SYNCED,
            customerCode = header.customerCode,
            customerName = header.customerName,
            customerAddress = header.customerAddress,
            subTotal = header.subTotal,
            // Hai khoản giảm nằm ở hai cột nhưng người xem chỉ cần một dòng.
            discountAmount = header.discountAmount + header.manualDiscount,
            vatAmount = header.vatAmount,
            totalAmount = header.totalAmount,
            note = header.note,
            lines = lines.map {
                OrderDetailLine(
                    id = it.id,
                    productCode = it.productCode,
                    productName = it.productName,
                    uomCode = it.uomCode,
                    qty = it.qty,
                    price = it.price,
                    discountAmount = it.discountAmount,
                    lineAmount = it.lineAmount,
                    isFreeItem = it.isFreeItem,
                )
            },
        )
    }

    private fun fromDate(days: Int): String =
        LocalDate.now().minusDays(days.toLong()).format(ISO)

    private companion object {
        val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val SYNCED = "SYNCED"
    }
}
