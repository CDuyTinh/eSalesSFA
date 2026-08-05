package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.visit.ActiveVisit
import com.tinhcd.esalessfa.domain.model.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.model.visit.CheckInPhoto
import com.tinhcd.esalessfa.domain.model.visit.CheckInResult
import com.tinhcd.esalessfa.domain.model.visit.LocationSample
import com.tinhcd.esalessfa.domain.model.visit.OpenVisit
import com.tinhcd.esalessfa.domain.model.visit.ReasonCode
import kotlinx.coroutines.flow.Flow

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
     * Nén, đóng dấu rồi cất ảnh vừa chụp; trả về ảnh đã xử lý.
     *
     * Xử lý ngay lúc chụp chứ không đợi tới lúc check-in: ảnh thô 3–8 MB nằm
     * trong cache của camera có thể bị hệ thống thu hồi bất cứ lúc nào, và làm
     * sớm thì lúc bấm check-in không phải chờ.
     */
    suspend fun prepareCheckInPhoto(
        rawPath: String,
        location: GeoPoint?,
        customerName: String,
    ): CheckInPhoto

    /**
     * Xoá một ảnh đã xử lý nhưng không dùng tới nữa, ví dụ khi bấm "Chụp lại".
     *
     * Ảnh nằm trong cache và chỉ được dọn tự động khi máy hết chỗ, nên chụp lại
     * năm lần mà không xoá là để lại bốn file rác.
     */
    suspend fun discardCheckInPhoto(photo: CheckInPhoto)

    suspend fun checkIn(
        customerId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        reasonCode: String?,
        note: String?,
        photo: CheckInPhoto?,
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
