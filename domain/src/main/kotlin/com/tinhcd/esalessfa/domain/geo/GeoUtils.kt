package com.tinhcd.esalessfa.domain.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Toạ độ địa lý. Kiểu của domain — không dùng android.location.Location ở tầng này.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

object GeoUtils {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Khoảng cách giữa hai điểm theo công thức Haversine, đơn vị mét.
     *
     * Dùng cho xác thực check-in: so sánh vị trí nhân viên với toạ độ khách hàng.
     * Đặt ở :domain nên test được trên JVM, không cần emulator.
     */
    fun distanceInMeters(from: GeoPoint, to: GeoPoint): Double {
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    /** Vị trí [current] có nằm trong bán kính [radiusMeters] quanh [target] không. */
    fun isWithinRadius(current: GeoPoint, target: GeoPoint, radiusMeters: Double): Boolean =
        distanceInMeters(current, target) <= radiusMeters
}
