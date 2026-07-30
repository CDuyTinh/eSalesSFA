package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.LocationSample
import kotlinx.coroutines.flow.Flow

/** Mã lý do bắt buộc chọn khi thao tác vượt quy định. */
data class ReasonCode(val code: String, val name: String)

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

interface VisitRepository {

    /** Ngưỡng lấy từ app_configs trên server, không hardcode trong app. */
    suspend fun checkInConfig(): CheckInConfig

    suspend fun reasonCodes(applyFor: String): List<ReasonCode>

    /** Lượt ghé đang mở của khách hàng hôm nay, null nếu chưa check-in. */
    suspend fun openVisit(customerId: String): OpenVisit?

    /** Dạng Flow để màn hình cập nhật ngay sau khi check-in hoặc check-out. */
    fun observeOpenVisit(customerId: String): Flow<OpenVisit?>

    /**
     * Còn cửa hàng nào đang trong trạng thái check-in không.
     *
     * Quy tắc nghiệp vụ: chưa check-out thì chưa được đồng bộ lên, vì dữ liệu
     * của lượt ghé đó còn có thể thay đổi.
     */
    suspend fun hasOpenVisit(): Boolean

    /** Lượt ghé đang mở trên toàn app, null nếu không có. */
    fun observeActiveVisit(): Flow<ActiveVisit?>

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

    suspend fun checkIn(
        customerId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        reasonCode: String?,
        batteryPct: Int?,
    ): CheckInResult

    suspend fun checkOut(
        visitId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        note: String?,
    )

    fun observeTodayVisitCount(): Flow<Int>

    companion object {
        const val REASON_OVER_DISTANCE = "CHECKIN_OVER_DISTANCE"
    }
}
