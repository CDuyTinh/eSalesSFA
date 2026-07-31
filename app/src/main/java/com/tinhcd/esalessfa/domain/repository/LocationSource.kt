package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.visit.LocationSample
import kotlinx.coroutines.flow.Flow

/**
 * Cổng lấy vị trí thiết bị.
 *
 * Quy tắc check-in phụ thuộc toạ độ, nên vị trí phải là một cổng của domain:
 * ViewModel test được bằng bản giả phát ra chuỗi mẫu có sai số giảm dần, thay vì
 * phải có GPS thật.
 */
interface LocationSource {

    fun hasPermission(): Boolean

    /**
     * Luồng vị trí liên tục.
     *
     * @param intervalMs chu kỳ mong muốn. Check-in cần dày (1s) để nhanh có toạ
     *   độ tốt; tracking lộ trình thì thưa để đỡ tốn pin.
     */
    fun locationUpdates(intervalMs: Long = 1_000L): Flow<LocationSample>

    /**
     * Như [locationUpdates] nhưng chỉ phát ra mẫu có sai số NHỎ NHẤT tới hiện tại.
     *
     * GPS lúc mới bật thường báo accuracy 100m+ rồi mới siết dần; lấy mẫu cuối
     * thì người dùng bấm nhanh sẽ check-in bằng toạ độ tệ nhất.
     */
    fun bestLocation(intervalMs: Long = 1_000L): Flow<LocationSample>
}
