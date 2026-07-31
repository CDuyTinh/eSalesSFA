package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Ba khoảng thời gian của biểu đồ doanh số, theo đúng bản eSales gốc. */
enum class RevenueRange { THIS_WEEK, LAST_WEEK, THIS_MONTH }

/** Một cột trên biểu đồ: nhãn trục hoành và doanh số ngày đó. */
data class RevenuePoint(
    val label: String,
    val amount: Long,
    /** Cột của hôm nay được vẽ đậm hơn phần còn lại. */
    val isToday: Boolean = false,
)

data class RevenueSeries(
    val range: RevenueRange = RevenueRange.THIS_WEEK,
    val fromDate: LocalDate = LocalDate.now(),
    val toDate: LocalDate = LocalDate.now(),
    val points: List<RevenuePoint> = emptyList(),
    /**
     * Doanh số trung bình một ngày, tính trên số ngày ĐÃ TRÔI QUA của kỳ.
     *
     * Không chia cho toàn bộ số ngày trong kỳ: chọn "Tháng này" vào mùng 3 thì
     * chia cho 31 sẽ ra một mức trung bình thấp giả tạo, và đường trung bình vẽ
     * trên biểu đồ trở thành lời khen sai.
     */
    val averagePerDay: Long = 0,
) {
    val total: Long get() = points.sumOf { it.amount }
}

/**
 * Doanh số theo ngày của khoảng đang chọn.
 *
 * Mốc đầu/cuối của mỗi khoảng là quy ước nghiệp vụ chứ không phải chuyện giao
 * diện: tuần bắt đầu từ THỨ HAI như lịch làm việc ngoài thị trường, và "tháng
 * này" chạy tới ngày cuối tháng chứ không dừng ở hôm nay — phần còn lại vẽ cột
 * rỗng để nhân viên thấy còn bao nhiêu ngày để chạy.
 */
class ObserveRevenueSeriesUseCase @Inject constructor(
    private val repository: ReportRepository,
) {

    operator fun invoke(
        range: RevenueRange,
        today: LocalDate = LocalDate.now(),
    ): Flow<RevenueSeries> {
        val (from, to) = bounds(range, today)

        return repository.observeRevenueBetween(from.toString(), to.toString()).map { daily ->
            val points = daily.map { day ->
                val date = LocalDate.parse(day.date)
                RevenuePoint(
                    label = label(range, date),
                    amount = day.amount,
                    isToday = date == today,
                )
            }

            RevenueSeries(
                range = range,
                fromDate = from,
                toDate = to,
                points = points,
                averagePerDay = averagePerDay(points.sumOf { it.amount }, from, to, today),
            )
        }
    }

    /** Chia cho số ngày đã trôi qua trong kỳ; kỳ đã qua hẳn thì chia cho cả kỳ. */
    private fun averagePerDay(
        total: Long,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
    ): Long {
        val lastCountedDay = if (today.isBefore(to)) today else to
        val elapsedDays = ChronoUnit.DAYS.between(from, lastCountedDay).toInt() + 1
        return if (elapsedDays <= 0) 0 else total / elapsedDays
    }

    private fun bounds(range: RevenueRange, today: LocalDate): Pair<LocalDate, LocalDate> =
        when (range) {
            RevenueRange.THIS_WEEK -> {
                val monday = today.with(DayOfWeek.MONDAY)
                monday to monday.plusDays(6)
            }

            RevenueRange.LAST_WEEK -> {
                val monday = today.with(DayOfWeek.MONDAY).minusWeeks(1)
                monday to monday.plusDays(6)
            }

            RevenueRange.THIS_MONTH -> {
                val first = today.withDayOfMonth(1)
                first to first.withDayOfMonth(first.lengthOfMonth())
            }
        }

    /**
     * Tuần thì ghi thứ, tháng thì ghi ngày.
     *
     * Tháng có 30 cột nên nhãn phải ngắn, còn tuần chỉ 7 cột nên ghi "T2".."CN"
     * dễ đọc hơn là ghi ngày.
     */
    private fun label(range: RevenueRange, date: LocalDate): String = when (range) {
        RevenueRange.THIS_MONTH -> date.dayOfMonth.toString()
        else -> WEEKDAY_LABELS[date.dayOfWeek.value - 1]
    }

    private companion object {
        val WEEKDAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
    }
}

/** Số ngày của khoảng, dùng cho test và cho phần hiển thị mốc thời gian. */
fun RevenueSeries.dayCount(): Int = ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1
