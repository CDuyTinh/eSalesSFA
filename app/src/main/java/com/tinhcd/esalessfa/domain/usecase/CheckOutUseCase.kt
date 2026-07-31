package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.OpenVisit
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.CheckInValidator
import com.tinhcd.esalessfa.domain.visit.LocationSample
import javax.inject.Inject

sealed interface CheckOutResult {

    data object Done : CheckOutResult

    /** Chưa đủ thời gian ghé tối thiểu; [minutesLeft] để báo cho nhân viên. */
    data class TooEarly(val minutesLeft: Int) : CheckOutResult
}

/**
 * Kết thúc lượt ghé, kèm quy tắc thời gian tối thiểu.
 *
 * Phép trừ ra "còn mấy phút nữa" trước đây nằm trong ViewModel, ngay cạnh
 * [CheckInValidator.canCheckOut] — hai mảnh của cùng một quy tắc ở hai tầng khác
 * nhau. Gom về đây thì đổi ngưỡng chỉ phải sửa một chỗ, và quy tắc test được mà
 * không cần dựng ViewModel.
 */
class CheckOutUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
) {

    suspend operator fun invoke(
        visit: OpenVisit,
        config: CheckInConfig,
        sample: LocationSample?,
        distanceMeters: Double?,
        note: String?,
        now: Long = System.currentTimeMillis(),
    ): CheckOutResult {
        if (!CheckInValidator.canCheckOut(visit.checkInAt, now, config)) {
            val elapsedMinutes = CheckInValidator.visitDurationMinutes(visit.checkInAt, now)
            return CheckOutResult.TooEarly(
                minutesLeft = (config.minVisitMinutes - elapsedMinutes).coerceAtLeast(1)
            )
        }

        visitRepository.checkOut(
            visitId = visit.id,
            sample = sample,
            distanceMeters = distanceMeters,
            note = note,
        )
        return CheckOutResult.Done
    }
}
