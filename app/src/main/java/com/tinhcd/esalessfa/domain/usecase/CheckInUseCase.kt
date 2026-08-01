package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.visit.CheckInValidation
import com.tinhcd.esalessfa.domain.visit.LocationSample
import com.tinhcd.esalessfa.domain.visit.distanceMetersOrNull
import javax.inject.Inject

sealed interface CheckInOutcome {

    data class Success(val visitId: String) : CheckInOutcome

    /** Đã có lượt ghé đang mở — tại chính cửa hàng này hoặc ở nơi khác. */
    data class AlreadyOpen(val customerName: String, val isSameCustomer: Boolean) : CheckInOutcome

    /** Vị trí chưa đủ điều kiện, hoặc ngoài bán kính mà chưa chọn lý do. */
    data object Rejected : CheckInOutcome
}

/**
 * Mở một lượt ghé.
 *
 * Gom ba mảnh trước đây nằm rải trong ViewModel: chỉ [CheckInValidation.Valid]
 * và [CheckInValidation.OverDistance] mới được đi tiếp, ngoài bán kính thì BẮT
 * BUỘC có lý do, và khoảng cách ghi vào lượt ghé lấy từ chính kết quả xác thực
 * chứ không đo lại. Đây đều là quy tắc nghiệp vụ, không phải chuyện giao diện.
 */
class CheckInUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
) {

    suspend operator fun invoke(
        customerId: String,
        sample: LocationSample?,
        validation: CheckInValidation,
        reasonCode: String?,
        note: String?,
        batteryPct: Int?,
    ): CheckInOutcome {
        val allowed = validation is CheckInValidation.Valid ||
            validation is CheckInValidation.OverDistance
        if (!allowed) return CheckInOutcome.Rejected

        if (validation is CheckInValidation.OverDistance && reasonCode.isNullOrBlank()) {
            return CheckInOutcome.Rejected
        }

        val result = visitRepository.checkIn(
            customerId = customerId,
            sample = sample,
            distanceMeters = validation.distanceMetersOrNull,
            reasonCode = reasonCode,
            note = note,
            batteryPct = batteryPct,
        )

        return when (result) {
            is VisitRepository.CheckInResult.Success -> CheckInOutcome.Success(result.visitId)

            is VisitRepository.CheckInResult.AlreadyOpen -> CheckInOutcome.AlreadyOpen(
                customerName = result.visit.customerName,
                isSameCustomer = result.isSameCustomer,
            )
        }
    }
}
