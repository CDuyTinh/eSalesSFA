package com.tinhcd.esalessfa.domain.model.visit

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint

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

/** Mã lý do bắt buộc chọn khi thao tác vượt quy định. */
data class ReasonCode(val code: String, val name: String)

/**
 * Ảnh check-in đã nén và đóng dấu, chờ gắn vào lượt ghé.
 *
 * Ảnh được chụp TRƯỚC khi lượt ghé tồn tại nên phải sống tạm ở đây một quãng;
 * VisitRepository.checkIn mới là chỗ ghi nó vào bản ghi và xếp hàng đẩy lên Storage.
 */
data class CheckInPhoto(val path: String, val sizeBytes: Int)

data class OpenVisit(
    val id: String,
    val customerId: String,
    val checkInAt: Long,
)

/** Lượt ghé đang mở, kèm tên cửa hàng để hiển thị lý do bị chặn. */
data class ActiveVisit(
    val visitId: String,
    val customerId: String,
    val customerName: String,
    val checkInAt: Long,
)

/**
 * Kết quả check-in.
 *
 * Trả về kiểu có nhánh thay vì ném lỗi hay trả String rỗng: nơi gọi buộc
 * phải xử lý trường hợp bị từ chối, không thể lỡ tay bỏ qua.
 */
sealed interface CheckInResult {
    data class Success(val visitId: String) : CheckInResult

    /** Đã có lượt ghé đang mở — tại chính cửa hàng này hoặc ở nơi khác. */
    data class AlreadyOpen(val visit: ActiveVisit, val isSameCustomer: Boolean) : CheckInResult
}

/**
 * Trạng thái cổng nghiệp vụ tại một cửa hàng.
 *
 * Gom ba khả năng vào một kiểu thay vì rải cờ boolean: mọi màn hình đọc cùng
 * một nguồn và trình biên dịch bắt buộc xử lý đủ ba nhánh.
 */
sealed interface VisitGate {

    /**
     * Chỉ được TẠO lượt ghé mới khi chưa có lượt nào đang mở.
     *
     * Đang ghé chính cửa hàng này cũng phải chặn — nếu không sẽ sinh lượt ghé
     * thứ hai và không lượt nào có giờ ra đúng.
     */
    fun canCheckIn(): Boolean = this is CanCheckIn

    /**
     * Có mở được màn viếng thăm không.
     *
     * Khác [canCheckIn]: màn đó dùng cho CẢ check-in và check-out, nên đang ghé
     * chính cửa hàng này vẫn phải vào được — đó là đường duy nhất để check-out.
     */
    fun canOpenVisitScreen(): Boolean = this !is BlockedByOther

    /** Chỉ cần toạ độ khách hàng khi sắp check-in; check-out thì không. */
    fun requiresCustomerLocation(): Boolean = this is CanCheckIn

    /** Kiểm kê, khảo sát, đặt hàng chỉ mở khi đang ghé chính cửa hàng này. */
    fun canDoBusiness(): Boolean = this is CheckedInHere

    /** Chưa ghé đâu cả — được phép check-in, chưa được thao tác nghiệp vụ. */
    data object CanCheckIn : VisitGate

    /** Đang ghé chính cửa hàng này — mở toàn bộ thao tác. */
    data class CheckedInHere(val visit: ActiveVisit) : VisitGate

    /** Đang ghé cửa hàng KHÁC — chặn tất cả, kể cả check-in. */
    data class BlockedByOther(val visit: ActiveVisit) : VisitGate
}
