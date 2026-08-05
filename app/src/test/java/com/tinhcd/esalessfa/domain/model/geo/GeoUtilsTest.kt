package com.tinhcd.esalessfa.domain.model.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoUtilsTest {

    // Chợ Bến Thành
    private val benThanh = GeoPoint(10.772461, 106.698055)

    // Nhà thờ Đức Bà — cách Bến Thành khoảng 850m đường chim bay
    private val nhaThoDucBa = GeoPoint(10.779783, 106.699018)

    @Test
    fun `distance to itself is zero`() {
        assertThat(GeoUtils.distanceInMeters(benThanh, benThanh)).isWithin(0.001).of(0.0)
    }

    @Test
    fun `distance between two known points`() {
        val distance = GeoUtils.distanceInMeters(benThanh, nhaThoDucBa)
        assertThat(distance).isWithin(50.0).of(820.0)
    }

    @Test
    fun `distance is symmetric`() {
        val forward = GeoUtils.distanceInMeters(benThanh, nhaThoDucBa)
        val backward = GeoUtils.distanceInMeters(nhaThoDucBa, benThanh)
        assertThat(forward).isWithin(0.001).of(backward)
    }

    @Test
    fun `point inside radius passes check-in validation`() {
        assertThat(GeoUtils.isWithinRadius(benThanh, nhaThoDucBa, radiusMeters = 1000.0)).isTrue()
    }

    @Test
    fun `point outside radius fails check-in validation`() {
        assertThat(GeoUtils.isWithinRadius(benThanh, nhaThoDucBa, radiusMeters = 100.0)).isFalse()
    }
}
