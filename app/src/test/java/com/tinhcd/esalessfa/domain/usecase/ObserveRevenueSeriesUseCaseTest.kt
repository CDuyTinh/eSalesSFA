package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class ObserveRevenueSeriesUseCaseTest {

    /** Thứ năm, 16/07/2026 — tuần chứa nó là 13/07 (T2) đến 19/07 (CN). */
    private val today = LocalDate.of(2026, 7, 16)

    @Test
    fun `tuan nay chay tu thu hai toi chu nhat`() = runTest {
        val series = ObserveRevenueSeriesUseCase(FakeReportRepository())(
            RevenueRange.THIS_WEEK,
            today,
        ).first()

        assertThat(series.fromDate).isEqualTo(LocalDate.of(2026, 7, 13))
        assertThat(series.toDate).isEqualTo(LocalDate.of(2026, 7, 19))
        assertThat(series.points.map { it.label })
            .containsExactly("T2", "T3", "T4", "T5", "T6", "T7", "CN").inOrder()
    }

    @Test
    fun `tuan truoc lui dung mot tuan`() = runTest {
        val series = ObserveRevenueSeriesUseCase(FakeReportRepository())(
            RevenueRange.LAST_WEEK,
            today,
        ).first()

        assertThat(series.fromDate).isEqualTo(LocalDate.of(2026, 7, 6))
        assertThat(series.toDate).isEqualTo(LocalDate.of(2026, 7, 12))
        assertThat(series.dayCount()).isEqualTo(7)
    }

    /**
     * Tháng chạy tới ngày cuối tháng chứ không dừng ở hôm nay: mấy cột rỗng cuối
     * biểu đồ chính là số ngày còn lại để chạy doanh số.
     */
    @Test
    fun `thang nay chay tu mung 1 toi ngay cuoi thang`() = runTest {
        val series = ObserveRevenueSeriesUseCase(FakeReportRepository())(
            RevenueRange.THIS_MONTH,
            today,
        ).first()

        assertThat(series.fromDate).isEqualTo(LocalDate.of(2026, 7, 1))
        assertThat(series.toDate).isEqualTo(LocalDate.of(2026, 7, 31))
        assertThat(series.points).hasSize(31)
        // Nhãn tháng ghi ngày, không ghi thứ.
        assertThat(series.points.first().label).isEqualTo("1")
        assertThat(series.points.last().label).isEqualTo("31")
    }

    /** Cột hôm nay được đánh dấu để biểu đồ vẽ đậm hơn phần còn lại. */
    @Test
    fun `danh dau dung cot cua hom nay`() = runTest {
        val series = ObserveRevenueSeriesUseCase(FakeReportRepository())(
            RevenueRange.THIS_WEEK,
            today,
        ).first()

        // 16/07 là thứ năm -> cột thứ tư trong tuần bắt đầu từ thứ hai.
        assertThat(series.points.indexOfFirst { it.isToday }).isEqualTo(3)
        assertThat(series.points.count { it.isToday }).isEqualTo(1)
    }

    @Test
    fun `tuan da qua han thi khong co cot nao la hom nay`() = runTest {
        val series = ObserveRevenueSeriesUseCase(FakeReportRepository())(
            RevenueRange.LAST_WEEK,
            today,
        ).first()

        assertThat(series.points.none { it.isToday }).isTrue()
    }

    /**
     * Trung bình chia cho số ngày ĐÃ TRÔI QUA: 01/07 tới 16/07 là 16 ngày, không
     * phải 31 ngày của cả tháng.
     */
    @Test
    fun `trung binh thang chia cho so ngay da troi qua`() = runTest {
        val repository = FakeReportRepository(
            revenueByDate = mapOf(
                "2026-07-02" to 10_000_000L,
                "2026-07-10" to 6_000_000L,
            )
        )

        val series = ObserveRevenueSeriesUseCase(repository)(RevenueRange.THIS_MONTH, today).first()

        assertThat(series.total).isEqualTo(16_000_000L)
        assertThat(series.averagePerDay).isEqualTo(1_000_000L)
    }

    /** Kỳ đã kết thúc thì chia cho trọn kỳ, không dừng ở hôm nay. */
    @Test
    fun `trung binh tuan truoc chia cho ca bay ngay`() = runTest {
        val repository = FakeReportRepository(
            revenueByDate = mapOf("2026-07-06" to 7_000_000L)
        )

        val series = ObserveRevenueSeriesUseCase(repository)(RevenueRange.LAST_WEEK, today).first()

        assertThat(series.averagePerDay).isEqualTo(1_000_000L)
    }

    @Test
    fun `tong cua khoang bang tong cac ngay trong khoang`() = runTest {
        val repository = FakeReportRepository(
            revenueByDate = mapOf(
                "2026-07-13" to 5_000_000L,
                "2026-07-15" to 3_000_000L,
                // Nằm ngoài tuần này, không được cộng vào.
                "2026-07-20" to 9_000_000L,
            )
        )

        val series = ObserveRevenueSeriesUseCase(repository)(RevenueRange.THIS_WEEK, today).first()

        assertThat(series.total).isEqualTo(8_000_000L)
        assertThat(series.points.first().amount).isEqualTo(5_000_000L)
    }
}
