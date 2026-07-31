package com.tinhcd.esalessfa.domain.visit

import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.geo.GeoUtils

/**
 * Một lần đọc vị trí từ thiết bị.
 *
 * [accuracy] là bán kính sai số theo mét mà hệ điều hành báo về — GPS trong nhà
 * hoặc giữa nhà cao tầng có thể lệch hàng trăm mét, nên phải kiểm tra trước khi
 * dùng để kết luận nhân viên có mặt tại cửa hàng hay không.
 */
data class LocationSample(
    val point: GeoPoint,
    val accuracy: Float,
    val isMock: Boolean = false,
    val capturedAt: Long = 0L,
)

/**
 * Cấu hình lấy từ bảng app_configs trên server, không hardcode trong app.
 *
 * Mỗi khách hàng doanh nghiệp có ngưỡng khác nhau: kênh siêu thị cho phép bán
 * kính rộng vì cửa hàng lớn, kênh tạp hoá thì siết chặt hơn.
 */
data class CheckInConfig(
    val radiusMeters: Double = 100.0,
    val maxAccuracyMeters: Float = 50f,
    /** 0 = tắt, 1 = cả accuracy và khoảng cách, 2 = chỉ accuracy, 3 = chỉ khoảng cách. */
    val validateType: Int = 1,
    val minVisitMinutes: Int = 5,
    val blockMockLocation: Boolean = true,
) {
    val checksAccuracy: Boolean get() = validateType == 1 || validateType == 2
    val checksDistance: Boolean get() = validateType == 1 || validateType == 3
}

/** Kết quả xác thực. Chỉ [Valid] và [OverDistance] mới cho phép đi tiếp. */
sealed interface CheckInValidation {

    data class Valid(val distanceMeters: Double?) : CheckInValidation

    /** Chưa lấy được vị trí — phải chờ thêm, không cho check-in mù. */
    data object NoLocation : CheckInValidation

    /** GPS quá nhiễu để kết luận. Chặn hẳn vì cho qua là dữ liệu rác. */
    data class AccuracyTooLow(val accuracy: Float, val required: Float) : CheckInValidation

    /**
     * Ngoài bán kính cho phép — KHÔNG chặn mà bắt chọn lý do.
     *
     * Cửa hàng dời địa điểm, toạ độ master sai, hoặc gặp khách ngoài cửa hàng
     * đều là tình huống có thật ngoài thị trường. Chặn cứng sẽ khiến nhân viên
     * không làm việc được; ghi lại lý do vừa cho họ đi tiếp vừa để quản lý soi.
     */
    data class OverDistance(
        val distanceMeters: Double,
        val allowedMeters: Double,
    ) : CheckInValidation

    /** Phát hiện giả lập vị trí. */
    data object MockLocation : CheckInValidation

    /** Khách hàng chưa có toạ độ trong master data. */
    data object NoCustomerLocation : CheckInValidation
}

/**
 * Khoảng cách đã đo trong lúc xác thực, nếu kết quả có mang theo.
 *
 * Lượt ghé ghi lại đúng con số này chứ không đo lại: đo lần hai bằng toạ độ mới
 * sẽ ra số khác với số vừa hiện trên màn hình cho nhân viên.
 */
val CheckInValidation.distanceMetersOrNull: Double?
    get() = when (this) {
        is CheckInValidation.Valid -> distanceMeters
        is CheckInValidation.OverDistance -> distanceMeters
        else -> null
    }

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
