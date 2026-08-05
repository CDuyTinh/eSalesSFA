package com.tinhcd.esalessfa.domain.model.geo

/**
 * Toạ độ địa lý. Kiểu của domain — không dùng android.location.Location ở tầng này.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)
