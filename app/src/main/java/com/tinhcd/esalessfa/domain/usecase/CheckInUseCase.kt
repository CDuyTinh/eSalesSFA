package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.CheckInPhoto
import com.tinhcd.esalessfa.domain.repository.PhotoUploader
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
 * Gom các quy tắc trước đây nằm rải trong ViewModel: chỉ [CheckInValidation.Valid]
 * và [CheckInValidation.OverDistance] mới được đi tiếp, ngoài bán kính thì BẮT
 * BUỘC có lý do, đứng trong bán kính thì BẮT BUỘC có ảnh, và khoảng cách ghi vào
 * lượt ghé lấy từ chính kết quả xác thực chứ không đo lại. Đây đều là quy tắc
 * nghiệp vụ, không phải chuyện giao diện.
 *
 * Vì sao ảnh chỉ bắt buộc khi ĐỨNG TRONG bán kính: đó là lúc hệ thống công nhận
 * nhân viên có mặt tại cửa hàng, nên cần một minh chứng nhìn được đi kèm toạ độ.
 * Ngoài bán kính thì lượt ghé đã mang sẵn lý do và bị soi riêng.
 */
class CheckInUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
    private val photoUploader: PhotoUploader,
) {

    suspend operator fun invoke(
        customerId: String,
        sample: LocationSample?,
        validation: CheckInValidation,
        reasonCode: String?,
        note: String?,
        photo: CheckInPhoto?,
        batteryPct: Int?,
    ): CheckInOutcome {
        val allowed = validation is CheckInValidation.Valid ||
            validation is CheckInValidation.OverDistance
        if (!allowed) return CheckInOutcome.Rejected

        if (validation is CheckInValidation.OverDistance && reasonCode.isNullOrBlank()) {
            return CheckInOutcome.Rejected
        }

        if (validation is CheckInValidation.Valid && photo == null) {
            return CheckInOutcome.Rejected
        }

        val result = visitRepository.checkIn(
            customerId = customerId,
            sample = sample,
            distanceMeters = validation.distanceMetersOrNull,
            reasonCode = reasonCode,
            note = note,
            photo = photo,
            batteryPct = batteryPct,
        )

        return when (result) {
            is VisitRepository.CheckInResult.Success -> {
                // Đẩy ảnh đi ngay chứ không đợi tới lượt sync giao dịch: ảnh nằm
                // trong cache máy, để lâu là mất, mà nhân viên còn ở trong cửa
                // hàng thì sóng thường tốt hơn lúc đang di chuyển.
                if (photo != null) photoUploader.start()
                CheckInOutcome.Success(result.visitId)
            }

            is VisitRepository.CheckInResult.AlreadyOpen -> CheckInOutcome.AlreadyOpen(
                customerName = result.visit.customerName,
                isSameCustomer = result.isSameCustomer,
            )
        }
    }
}
