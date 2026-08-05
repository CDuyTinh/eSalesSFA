package com.tinhcd.esalessfa.domain.model.visit

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.geo.GeoUtils

object CheckInValidator {

    fun validate(
        sample: LocationSample?,
        customerLocation: GeoPoint?,
        config: CheckInConfig,
    ): CheckInValidation {
        if (config.validateType == 0) {
            return CheckInValidation.Valid(
                distanceMeters = distanceOrNull(sample, customerLocation)
            )
        }

        if (sample == null) return CheckInValidation.NoLocation

        // Kiểm tra giả lập trước mọi thứ khác: toạ độ giả có accuracy đẹp và
        // khoảng cách bằng 0, nên nếu xét sau thì nó luôn lọt qua.
        if (config.blockMockLocation && sample.isMock) return CheckInValidation.MockLocation

        if (config.checksAccuracy && sample.accuracy > config.maxAccuracyMeters) {
            return CheckInValidation.AccuracyTooLow(sample.accuracy, config.maxAccuracyMeters)
        }

        if (!config.checksDistance) return CheckInValidation.Valid(null)

        if (customerLocation == null) return CheckInValidation.NoCustomerLocation

        val distance = GeoUtils.distanceInMeters(sample.point, customerLocation)
        return if (distance <= config.radiusMeters) {
            CheckInValidation.Valid(distance)
        } else {
            CheckInValidation.OverDistance(distance, config.radiusMeters)
        }
    }

    /**
     * Được phép check-out chưa.
     *
     * Chặn thời gian tối thiểu để ngăn kiểu "ghé cho có" — vào rồi ra ngay mà
     * không thực sự làm việc với cửa hàng.
     */
    fun canCheckOut(
        checkInAt: Long,
        now: Long,
        config: CheckInConfig,
    ): Boolean = (now - checkInAt) >= config.minVisitMinutes * 60_000L

    fun visitDurationMinutes(checkInAt: Long, checkOutAt: Long): Int =
        ((checkOutAt - checkInAt) / 60_000L).toInt().coerceAtLeast(0)

    private fun distanceOrNull(sample: LocationSample?, target: GeoPoint?): Double? =
        if (sample != null && target != null) {
            GeoUtils.distanceInMeters(sample.point, target)
        } else {
            null
        }
}
