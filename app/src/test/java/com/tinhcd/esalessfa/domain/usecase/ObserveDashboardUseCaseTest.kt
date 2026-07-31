package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.repository.MonthStats
import com.tinhcd.esalessfa.domain.repository.TodayStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class ObserveDashboardUseCaseTest {

    /** Thứ năm, 16/07/2026. Cố định để phép đếm "thứ đã trôi qua" kiểm được. */
    private val today = LocalDate.of(2026, 7, 16)

    @Test
    fun `chi tieu don hang va viec tham la so khach phai ghe hom nay`() = runTest {
        val repository = FakeReportRepository(
            today = TodayStats(
                revenue = 12_480_000,
                orderCount = 4,
                visitedCount = 3,
                routePlanCount = 32,
                skuLines = 14,
            )
        )

        val day = ObserveDashboardUseCase(repository)(today).first().day

        assertThat(day.revenue).isEqualTo(12_480_000)
        assertThat(day.orders.actual).isEqualTo(4.0)
        assertThat(day.orders.target).isEqualTo(32)
        assertThat(day.visits.actual).isEqualTo(3.0)
        assertThat(day.visits.target).isEqualTo(32)
        // 14 cặp (đơn, mặt hàng) trên 4 đơn.
        assertThat(day.skuPerOrder.actual).isEqualTo(3.5)
        // SKU/Đơn hàng không có chỉ tiêu.
        assertThat(day.skuPerOrder.target).isNull()
    }

    @Test
    fun `chua co don nao thi SKU tren don la 0 chu khong chia cho 0`() = runTest {
        val repository = FakeReportRepository(today = TodayStats(orderCount = 0, skuLines = 0))

        val day = ObserveDashboardUseCase(repository)(today).first().day

        assertThat(day.skuPerOrder.actual).isEqualTo(0.0)
    }

    /** Tuyến lưu theo Calendar.DAY_OF_WEEK: thứ năm = 5. */
    @Test
    fun `hoi dung tuyen cua thu trong tuan`() = runTest {
        val repository = FakeReportRepository()

        ObserveDashboardUseCase(repository)(today).first()

        assertThat(repository.requestedDayOfWeek).isEqualTo(5)
    }

    /**
     * Từ 01/07 tới 16/07/2026 có 3 thứ năm (2, 9, 16) và 2 thứ hai (6, 13).
     * Tuyến: thứ năm 10 khách, thứ hai 8 khách -> kế hoạch = 3*10 + 2*8 = 46.
     */
    @Test
    fun `chi tieu ghe thang chi tinh toi hom nay`() = runTest {
        val repository = FakeReportRepository(
            month = MonthStats(
                visitedCount = 23,
                routePlanByWeekday = mapOf(5 to 10, 2 to 8),
            )
        )

        val kpi = ObserveDashboardUseCase(repository)(today).first()
            .monthKpis.first { it.type == MonthKpiType.VISIT_COVERAGE }

        assertThat(kpi.target).isEqualTo(46.0)
        assertThat(kpi.actual).isEqualTo(23.0)
        assertThat(kpi.percent).isEqualTo(50)
    }

    /** PC đếm lượt ghé CÓ đơn trên tổng lượt ghé — không phải số đơn trên số ghé. */
    @Test
    fun `PC la ty le luot ghe co don tren tong luot ghe`() = runTest {
        val repository = FakeReportRepository(
            month = MonthStats(
                // 30 đơn nhưng chỉ 18 lượt ghé sinh ra đơn: có cửa hàng viết hai
                // đơn trong cùng một lượt.
                orderCount = 30,
                productiveVisitCount = 18,
                visitedCount = 24,
            )
        )

        val kpi = ObserveDashboardUseCase(repository)(today).first()
            .monthKpis.first { it.type == MonthKpiType.PRODUCTIVE_CALL }

        assertThat(kpi.actual).isEqualTo(18.0)
        assertThat(kpi.percent).isEqualTo(75)
    }

    @Test
    fun `do phu khach hang la khach da mua tren khach duoc phan tuyen`() = runTest {
        val repository = FakeReportRepository(
            month = MonthStats(buyingCustomerCount = 30, routeCustomerCount = 120)
        )

        val kpi = ObserveDashboardUseCase(repository)(today).first()
            .monthKpis.first { it.type == MonthKpiType.CUSTOMER_COVERAGE }

        assertThat(kpi.percent).isEqualTo(25)
    }

    /** Vượt chỉ tiêu vẫn chỉ vẽ đầy vòng tròn, không tràn ra 150%. */
    @Test
    fun `vuot chi tieu thi ty le dung o 100 phan tram`() = runTest {
        val repository = FakeReportRepository(
            month = MonthStats(buyingCustomerCount = 300, routeCustomerCount = 200)
        )

        val kpi = ObserveDashboardUseCase(repository)(today).first()
            .monthKpis.first { it.type == MonthKpiType.CUSTOMER_COVERAGE }

        assertThat(kpi.ratio).isEqualTo(1.0)
        assertThat(kpi.actual).isEqualTo(300.0)
    }

    /** Chưa ghé lần nào thì mẫu số bằng 0 — không được ra NaN hay vô cực. */
    @Test
    fun `mau so bang 0 thi ty le bang 0`() = runTest {
        val repository = FakeReportRepository(month = MonthStats())

        val kpis = ObserveDashboardUseCase(repository)(today).first().monthKpis

        assertThat(kpis.map { it.percent }).containsExactly(0, 0, 0, 0)
    }
}
