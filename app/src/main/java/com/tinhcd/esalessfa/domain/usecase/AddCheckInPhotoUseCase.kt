package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.visit.CheckInPhoto
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import javax.inject.Inject

/**
 * Nhận ảnh vừa chụp ở màn check-in: nén, đóng dấu, rồi bỏ ảnh cũ nếu có.
 *
 * Đối xứng với [AddSurveyPhotoUseCase] của luồng khảo sát — cùng một việc thì
 * phải nằm cùng một tầng, nếu không người đọc sẽ tưởng hai luồng khác nhau về
 * bản chất.
 *
 * Thứ tự "chuẩn bị ảnh mới TRƯỚC, bỏ ảnh cũ SAU" là quy tắc chứ không phải
 * chuyện sắp câu lệnh: xử lý ảnh có thể hỏng giữa chừng (hết bộ nhớ, file lỗi),
 * bỏ ảnh cũ trước thì nhân viên mất luôn tấm đã chụp và phải chụp lại từ đầu.
 */
class AddCheckInPhotoUseCase @Inject constructor(
    private val visitRepository: VisitRepository,
) {

    suspend operator fun invoke(
        rawPath: String,
        previous: CheckInPhoto?,
        location: GeoPoint?,
        customerName: String,
    ): CheckInPhoto {
        val photo = visitRepository.prepareCheckInPhoto(
            rawPath = rawPath,
            location = location,
            customerName = customerName,
        )
        previous?.let { visitRepository.discardCheckInPhoto(it) }
        return photo
    }
}
