package com.tinhcd.esalessfa.domain.model.promotion

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.model.order.MoneyMath
import org.junit.Test

/**
 * Phép tính tiền là chỗ sai âm thầm nhất: kết quả vẫn ra số, chỉ lệch vài đồng,
 * và chỉ lộ ra khi server từ chối đơn vì tổng không khớp.
 */
class MoneyMathTest {

    @Test
    fun `phan tram lam tron nua len`() {
        // 3% của 1.000.050 = 30.001,5 -> 30.002
        assertThat(MoneyMath.percentOf(1_000_050, 0.03)).isEqualTo(30_002L)
    }

    @Test
    fun `phan tram khong sinh sai so kieu double`() {
        // 0,1 + 0,2 != 0,3 trong Double. Qua BigDecimal thì đúng.
        val a = MoneyMath.percentOf(1_000_000, 0.1)
        val b = MoneyMath.percentOf(1_000_000, 0.2)
        val c = MoneyMath.percentOf(1_000_000, 0.3)

        assertThat(a + b).isEqualTo(c)
    }

    @Test
    fun `nhan so luong le van tron ve dong`() {
        assertThat(MoneyMath.multiply(33_333, 3.0)).isEqualTo(99_999L)
        assertThat(MoneyMath.multiply(10_000, 0.5)).isEqualTo(5_000L)
    }

    @Test
    fun `phan bo giu tong dung bang so ban dau`() {
        val parts = MoneyMath.allocate(100, listOf(1, 1, 1))

        assertThat(parts.sum()).isEqualTo(100L)
        // 33 + 33 + 34 — dòng cuối gánh phần dư.
        assertThat(parts).containsExactly(33L, 33L, 34L).inOrder()
    }

    @Test
    fun `phan bo theo trong so khong deu`() {
        val parts = MoneyMath.allocate(500_000, listOf(6_000_000, 4_000_000))

        assertThat(parts).containsExactly(300_000L, 200_000L).inOrder()
        assertThat(parts.sum()).isEqualTo(500_000L)
    }

    @Test
    fun `phan bo voi trong so bang khong tra ve khong`() {
        val parts = MoneyMath.allocate(1000, listOf(0, 0))

        assertThat(parts).containsExactly(0L, 0L)
    }

    @Test
    fun `phan bo danh sach rong khong nem loi`() {
        assertThat(MoneyMath.allocate(1000, emptyList())).isEmpty()
    }

    @Test
    fun `phan bo so tien le qua nhieu dong van khop tong`() {
        val weights = List(97) { 10_007L }
        val parts = MoneyMath.allocate(1_234_567, weights)

        assertThat(parts.sum()).isEqualTo(1_234_567L)
    }
}
