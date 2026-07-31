package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.CheckInValidation
import com.tinhcd.esalessfa.domain.visit.CheckInValidator
import com.tinhcd.esalessfa.domain.visit.LocationSample
import javax.inject.Inject

/**
 * Xác thực một lần đọc vị trí so với cửa hàng đang đứng.
 *
 * Mỏng nhưng cần thiết: nó là chỗ DUY NHẤT tầng presentation chạm vào quy tắc
 * check-in. Trước đây ViewModel gọi thẳng [CheckInValidator], nên quy tắc bị gọi
 * từ hai tầng và sửa một bên là lệch.
 */
class ValidateCheckInUseCase @Inject constructor() {

    operator fun invoke(
        sample: LocationSample?,
        customerLocation: GeoPoint?,
        config: CheckInConfig,
    ): CheckInValidation = CheckInValidator.validate(
        sample = sample,
        customerLocation = customerLocation,
        config = config,
    )
}
