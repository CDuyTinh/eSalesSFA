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

    /** Tên cửa hàng đang mở lượt ghé, dùng cho thông báo trên màn chính. */
    fun observeBlockingCustomerName(): Flow<String?>

    suspend fun checkIn(
        customerId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        reasonCode: String?,
        batteryPct: Int?,
    ): String

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
