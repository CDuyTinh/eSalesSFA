package com.tinhcd.esalessfa.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.visit.LocationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.scan
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bọc FusedLocationProviderClient thành Flow.
 *
 * Callback API buộc phải nhớ gỡ listener; callbackFlow gắn việc gỡ vào vòng đời
 * của coroutine nên không thể quên. Đóng màn hình là listener tự huỷ.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Luồng vị trí liên tục với độ chính xác cao.
     *
     * @param intervalMs chu kỳ mong muốn. Check-in cần dày (1s) để nhanh có toạ
     *   độ tốt; tracking lộ trình thì thưa (vài phút) để đỡ tốn pin.
     */
    fun locationUpdates(intervalMs: Long = 1_000L): Flow<LocationSample> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toSample()) }
            }
        }

        client.requestLocationUpdates(request, callback, context.mainLooper)

        awaitClose { client.removeLocationUpdates(callback) }
    }

    /**
     * Giữ lại mẫu có sai số NHỎ NHẤT thay vì mẫu mới nhất.
     *
     * GPS lúc mới bật thường báo accuracy 100m+ rồi mới siết dần. Nếu chỉ lấy
     * mẫu cuối, người dùng bấm nhanh sẽ check-in bằng toạ độ tệ nhất.
     */
    fun bestLocation(intervalMs: Long = 1_000L): Flow<LocationSample> =
        locationUpdates(intervalMs)
            .scan<LocationSample, LocationSample?>(null) { best, next ->
                if (best == null || next.accuracy < best.accuracy) next else best
            }
            .filterNotNull()

    private fun Location.toSample() = LocationSample(
        point = GeoPoint(latitude, longitude),
        accuracy = accuracy,
        // isMock thay cho isFromMockProvider đã deprecated; API 31+ mới có.
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        },
        capturedAt = System.currentTimeMillis(),
    )
}
