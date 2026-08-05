package com.tinhcd.esalessfa.domain.model.visit

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import org.junit.Test

class CheckInValidatorTest {

    private val shop = GeoPoint(10.772461, 106.698055)

    /** Cách [shop] khoảng 55m về phía bắc. */
    private val nearby = GeoPoint(10.772961, 106.698055)

    /** Cách [shop] khoảng 820m. */
    private val faraway = GeoPoint(10.779783, 106.699018)

    private fun sample(
        point: GeoPoint = nearby,
        accuracy: Float = 10f,
        isMock: Boolean = false,
    ) = LocationSample(point, accuracy, isMock)

    @Test
    fun `trong ban kinh thi hop le`() {
        val result = CheckInValidator.validate(sample(), shop, CheckInConfig())

        assertThat(result).isInstanceOf(CheckInValidation.Valid::class.java)
        assertThat((result as CheckInValidation.Valid).distanceMeters).isLessThan(100.0)
    }

    @Test
    fun `ngoai ban kinh thi doi ly do chu khong chan`() {
        val result = CheckInValidator.validate(sample(point = faraway), shop, CheckInConfig())

        assertThat(result).isInstanceOf(CheckInValidation.OverDistance::class.java)
        val over = result as CheckInValidation.OverDistance
        assertThat(over.distanceMeters).isGreaterThan(100.0)
        assertThat(over.allowedMeters).isEqualTo(100.0)
    }

    @Test
    fun `gps qua nhieu thi chan han`() {
        val result = CheckInValidator.validate(sample(accuracy = 120f), shop, CheckInConfig())

        assertThat(result).isInstanceOf(CheckInValidation.AccuracyTooLow::class.java)
    }

    @Test
    fun `chua co vi tri thi khong cho check-in mu`() {
        val result = CheckInValidator.validate(null, shop, CheckInConfig())

        assertThat(result).isEqualTo(CheckInValidation.NoLocation)
    }

    @Test
    fun `phat hien gia lap vi tri`() {
        val result = CheckInValidator.validate(sample(isMock = true), shop, CheckInConfig())

        assertThat(result).isEqualTo(CheckInValidation.MockLocation)
    }

    @Test
    fun `kiem tra gia lap truoc khi kiem tra khoang cach`() {
        // Toạ độ giả luôn trùng khít cửa hàng và accuracy đẹp. Nếu xét khoảng
        // cách trước thì nó lọt qua thành Valid.
        val perfectFake = LocationSample(shop, accuracy = 1f, isMock = true)

        val result = CheckInValidator.validate(perfectFake, shop, CheckInConfig())

        assertThat(result).isEqualTo(CheckInValidation.MockLocation)
    }

    @Test
    fun `tat kiem tra gia lap thi toa do gia van qua`() {
        val config = CheckInConfig(blockMockLocation = false)

        val result = CheckInValidator.validate(sample(isMock = true), shop, config)

        assertThat(result).isInstanceOf(CheckInValidation.Valid::class.java)
    }

    @Test
    fun `che do chi kiem tra accuracy thi bo qua khoang cach`() {
        val config = CheckInConfig(validateType = 2)

        val result = CheckInValidator.validate(sample(point = faraway), shop, config)

        assertThat(result).isInstanceOf(CheckInValidation.Valid::class.java)
    }

    @Test
    fun `che do chi kiem tra khoang cach thi bo qua accuracy`() {
        val config = CheckInConfig(validateType = 3)

        val result = CheckInValidator.validate(sample(accuracy = 500f), shop, config)

        assertThat(result).isInstanceOf(CheckInValidation.Valid::class.java)
    }

    @Test
    fun `tat toan bo kiem tra thi luon hop le`() {
        val config = CheckInConfig(validateType = 0)

        val result = CheckInValidator.validate(null, null, config)

        assertThat(result).isInstanceOf(CheckInValidation.Valid::class.java)
    }

    @Test
    fun `khach hang chua co toa do thi bao rieng`() {
        val result = CheckInValidator.validate(sample(), customerLocation = null, CheckInConfig())

        assertThat(result).isEqualTo(CheckInValidation.NoCustomerLocation)
    }

    @Test
    fun `chua du thoi gian toi thieu thi chua cho check-out`() {
        val checkIn = 1_000_000L
        val config = CheckInConfig(minVisitMinutes = 5)

        // Mới 3 phút
        assertThat(CheckInValidator.canCheckOut(checkIn, checkIn + 3 * 60_000L, config)).isFalse()
        // Đủ 5 phút
        assertThat(CheckInValidator.canCheckOut(checkIn, checkIn + 5 * 60_000L, config)).isTrue()
    }

    @Test
    fun `tinh dung thoi luong vieng tham`() {
        val checkIn = 1_000_000L

        assertThat(CheckInValidator.visitDurationMinutes(checkIn, checkIn + 17 * 60_000L))
            .isEqualTo(17)
    }

    @Test
    fun `thoi luong khong bao gio am du dong ho lech`() {
        val checkIn = 1_000_000L

        assertThat(CheckInValidator.visitDurationMinutes(checkIn, checkIn - 60_000L)).isEqualTo(0)
    }
}
