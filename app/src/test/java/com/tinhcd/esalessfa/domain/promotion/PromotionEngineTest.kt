package com.tinhcd.esalessfa.domain.promotion

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.promotion.model.ApplyLevel
import com.tinhcd.esalessfa.domain.promotion.model.DiscountKind
import com.tinhcd.esalessfa.domain.promotion.model.PromotionBreak
import org.junit.Test

class PromotionEngineTest {

    private val engine = PromotionEngine.default()

    // =========================================================================
    // Chiết khấu bậc theo số lượng
    // =========================================================================

    @Test
    fun `khong dat dieu kien thi khong co khuyen mai`() {
        val lines = listOf(Fixtures.line(qty = 9.0))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier()))

        assertThat(result.totalDiscount).isEqualTo(0L)
        assertThat(result.appliedProgramCodes).isEmpty()
    }

    @Test
    fun `dat bac 1 thi giam 3 phan tram`() {
        val lines = listOf(Fixtures.line(qty = 10.0, unitPrice = 100_000))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier()))

        // 10 x 100.000 = 1.000.000, giảm 3% = 30.000
        assertThat(result.totalDiscount).isEqualTo(30_000L)
    }

    @Test
    fun `chi lay bac cao nhat khi khong cong don`() {
        val lines = listOf(Fixtures.line(qty = 50.0, unitPrice = 100_000))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier(isMultiLevel = false)))

        // Thoả cả 3 bậc nhưng chỉ lấy 8%: 5.000.000 x 8% = 400.000
        assertThat(result.totalDiscount).isEqualTo(400_000L)
    }

    @Test
    fun `cong don moi bac khi bat multi level`() {
        val lines = listOf(Fixtures.line(qty = 50.0, unitPrice = 100_000))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier(isMultiLevel = true)))

        // 3% + 5% + 8% = 16% của 5.000.000 = 800.000
        assertThat(result.totalDiscount).isEqualTo(800_000L)
    }

    @Test
    fun `dieu kien so luong tinh tren don vi goc chu khong phai don vi ban`() {
        // 1 thùng = 24 lẻ, đủ điều kiện "mua 10" dù qty nhập chỉ là 1.
        val lines = listOf(
            Fixtures.line(qty = 1.0, conversionRate = 24.0, unitPrice = 2_400_000)
        )

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier()))

        // baseQty = 24 -> thoả bậc 2 (>=20) -> 5% của 2.400.000 = 120.000
        assertThat(result.totalDiscount).isEqualTo(120_000L)
    }

    // =========================================================================
    // Chiết khấu theo giá trị đơn
    // =========================================================================

    @Test
    fun `chiet khau toan don duoc phan bo ve tung dong`() {
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "P1", qty = 30.0, unitPrice = 200_000),
            Fixtures.line(lineNo = 2, productId = "P2", qty = 20.0, unitPrice = 200_000),
        )

        val result = engine.calculate(lines, listOf(Fixtures.amountTier()))

        // Tổng 10.000.000 -> bậc 2 -> 5% = 500.000
        assertThat(result.totalDiscount).isEqualTo(500_000L)
        // Phân bổ theo tỉ lệ 6.000.000 : 4.000.000
        assertThat(result.discountForLine(1)).isEqualTo(300_000L)
        assertThat(result.discountForLine(2)).isEqualTo(200_000L)
    }

    @Test
    fun `tong phan bo luon bang dung chiet khau du co so le`() {
        // Ba dòng giá trị lẻ để ép làm tròn xuất hiện.
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "P1", qty = 1.0, unitPrice = 3_333_333),
            Fixtures.line(lineNo = 2, productId = "P2", qty = 1.0, unitPrice = 3_333_333),
            Fixtures.line(lineNo = 3, productId = "P3", qty = 1.0, unitPrice = 3_333_334),
        )

        val result = engine.calculate(lines, listOf(Fixtures.amountTier()))

        val sumOfParts = (1..3).sumOf { result.discountForLine(it) }
        assertThat(sumOfParts).isEqualTo(result.totalDiscount)
    }

    // =========================================================================
    // Hàng tặng
    // =========================================================================

    @Test
    fun `mua 10 tang 1 tinh dung so suat`() {
        val lines = listOf(Fixtures.line(qty = 35.0))

        val result = engine.calculate(lines, listOf(Fixtures.freeItem()))

        // floor(35 / 10) = 3 suất
        assertThat(result.freeItems).hasSize(1)
        assertThat(result.freeItems.first().qty).isWithin(1e-9).of(3.0)
        assertThat(result.freeItems.first().productId).isEqualTo("P9")
    }

    @Test
    fun `het ton kho hang tang thi chi tang phan con lai`() {
        val lines = listOf(Fixtures.line(qty = 100.0))

        val result = engine.calculate(lines, listOf(Fixtures.freeItem(freeStockQty = 4.0)))

        // Đủ điều kiện 10 suất nhưng kho chỉ còn 4.
        assertThat(result.freeItems.first().qty).isWithin(1e-9).of(4.0)
    }

    @Test
    fun `khong tang khi ton kho bang khong`() {
        val lines = listOf(Fixtures.line(qty = 100.0))

        val result = engine.calculate(lines, listOf(Fixtures.freeItem(freeStockQty = 0.0)))

        assertThat(result.freeItems).isEmpty()
        assertThat(result.appliedProgramCodes).isEmpty()
    }

    @Test
    fun `bi chan tran so suat`() {
        val lines = listOf(Fixtures.line(qty = 100.0))
        val program = Fixtures.freeItem(
            breaks = listOf(PromotionBreak("f1", 1, minQty = 10.0, freeQty = 1.0, maxApplyTimes = 3))
        )

        val result = engine.calculate(lines, listOf(program))

        assertThat(result.freeItems.first().qty).isWithin(1e-9).of(3.0)
    }

    // =========================================================================
    // Combo bộ
    // =========================================================================

    @Test
    fun `thieu mot chan thi ca bo khong duoc tinh`() {
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "PA", qty = 5.0, unitPrice = 100_000),
            Fixtures.line(lineNo = 2, productId = "PB", qty = 5.0, unitPrice = 100_000),
            // thiếu PC
        )

        val result = engine.calculate(lines, listOf(Fixtures.combo()))

        assertThat(result.totalDiscount).isEqualTo(0L)
    }

    @Test
    fun `du ba chan thi tinh theo so bo hoan chinh`() {
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "PA", qty = 5.0, unitPrice = 100_000),
            Fixtures.line(lineNo = 2, productId = "PB", qty = 5.0, unitPrice = 100_000),
            Fixtures.line(lineNo = 3, productId = "PC", qty = 5.0, unitPrice = 100_000),
        )

        val result = engine.calculate(lines, listOf(Fixtures.combo()))

        // Tổng 1.500.000, giảm 10% = 150.000
        assertThat(result.totalDiscount).isEqualTo(150_000L)
    }

    @Test
    fun `so bo lay theo chan yeu nhat`() {
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "PA", qty = 10.0, unitPrice = 100_000),
            Fixtures.line(lineNo = 2, productId = "PB", qty = 10.0, unitPrice = 100_000),
            // PC chỉ có 2 -> tối đa 2 bộ
            Fixtures.line(lineNo = 3, productId = "PC", qty = 2.0, unitPrice = 100_000),
        )

        val program = Fixtures.combo(
            discountKind = DiscountKind.AMOUNT,
            discountAmount = 50_000,
            discountPct = 0.0,
        )
        val result = engine.calculate(lines, listOf(program))

        // 2 bộ x 50.000 = 100.000
        assertThat(result.totalDiscount).isEqualTo(100_000L)
    }

    // =========================================================================
    // Ngân sách
    // =========================================================================

    @Test
    fun `chiet khau bi cat theo ngan sach con lai`() {
        val lines = listOf(Fixtures.line(qty = 50.0, unitPrice = 100_000))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier(budget = 100_000)))

        // Đáng lẽ 400.000 nhưng ngân sách chỉ còn 100.000.
        assertThat(result.totalDiscount).isEqualTo(100_000L)
    }

    @Test
    fun `het ngan sach thi chuong trinh khong ap`() {
        val lines = listOf(Fixtures.line(qty = 50.0, unitPrice = 100_000))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier(budget = 0)))

        assertThat(result.totalDiscount).isEqualTo(0L)
        assertThat(result.appliedProgramCodes).isEmpty()
    }

    @Test
    fun `ngan sach tinh chung cho moi dong cua chuong trinh`() {
        val lines = listOf(
            Fixtures.line(lineNo = 1, productId = "P1", qty = 50.0, unitPrice = 100_000),
            Fixtures.line(lineNo = 2, productId = "P2", qty = 50.0, unitPrice = 100_000),
        )
        val program = Fixtures.qtyTier(productIds = listOf("P1", "P2"), budget = 500_000)

        val result = engine.calculate(lines, listOf(program))

        // Mỗi dòng đáng 400.000, tổng 800.000, nhưng trần chung là 500.000.
        assertThat(result.totalDiscount).isAtMost(500_000L)
    }

    // =========================================================================
    // Loại trừ chương trình
    // =========================================================================

    @Test
    fun `chuong trinh bi loai tru khong dung lai so luong da tieu`() {
        val lines = listOf(Fixtures.line(qty = 20.0, unitPrice = 100_000))

        val first = Fixtures.qtyTier(code = "KM01", priority = 1)
        val second = Fixtures.qtyTier(code = "KM02", priority = 2, excludes = setOf("KM01"))

        val result = engine.calculate(lines, listOf(first, second))

        // KM01 tiêu hết 20 -> KM02 còn 0 -> không đủ điều kiện.
        assertThat(result.appliedProgramCodes).containsExactly("KM01")
    }

    @Test
    fun `khong khai bao loai tru thi hai chuong trinh cung ap`() {
        val lines = listOf(Fixtures.line(qty = 20.0, unitPrice = 100_000))

        val first = Fixtures.qtyTier(code = "KM01", priority = 1)
        val second = Fixtures.qtyTier(code = "KM02", priority = 2)

        val result = engine.calculate(lines, listOf(first, second))

        assertThat(result.appliedProgramCodes).containsExactly("KM01", "KM02").inOrder()
    }

    @Test
    fun `chuong trinh chay theo thu tu priority`() {
        val lines = listOf(Fixtures.line(qty = 20.0, unitPrice = 100_000))

        val low = Fixtures.qtyTier(code = "SAU", priority = 50)
        val high = Fixtures.qtyTier(code = "TRUOC", priority = 1)

        val result = engine.calculate(lines, listOf(low, high))

        assertThat(result.appliedProgramCodes).containsExactly("TRUOC", "SAU").inOrder()
    }

    // =========================================================================
    // Chiết khấu tay
    // =========================================================================

    @Test
    fun `chiet khau tay bi chan theo tran phan tram`() {
        val lines = listOf(Fixtures.line(qty = 10.0, unitPrice = 100_000))
        // Đơn 1.000.000, trần 5% = 50.000, nhân viên xin 200.000.
        val engineWithManual = PromotionEngine.default(manualDiscountAmount = 200_000)

        val result = engineWithManual.calculate(lines, listOf(Fixtures.manual()))

        assertThat(result.totalDiscount).isEqualTo(50_000L)
    }

    @Test
    fun `chiet khau tay duoi tran thi giu nguyen`() {
        val lines = listOf(Fixtures.line(qty = 10.0, unitPrice = 100_000))
        val engineWithManual = PromotionEngine.default(manualDiscountAmount = 20_000)

        val result = engineWithManual.calculate(lines, listOf(Fixtures.manual()))

        assertThat(result.totalDiscount).isEqualTo(20_000L)
        assertThat(result.lineDiscounts.first().isManual).isTrue()
    }

    @Test
    fun `chiet khau tay khong chan chuong trinh tu dong`() {
        val lines = listOf(Fixtures.line(qty = 20.0, unitPrice = 100_000))
        val engineWithManual = PromotionEngine.default(manualDiscountAmount = 50_000)

        val result = engineWithManual.calculate(
            lines,
            listOf(Fixtures.manual(), Fixtures.qtyTier(code = "KM01", priority = 1)),
        )

        assertThat(result.appliedProgramCodes).containsExactly("KM01", "KM15")
    }

    // =========================================================================
    // Trường hợp biên
    // =========================================================================

    @Test
    fun `don rong khong sinh khuyen mai`() {
        val result = engine.calculate(emptyList(), listOf(Fixtures.qtyTier()))

        assertThat(result.totalDiscount).isEqualTo(0L)
        assertThat(result.freeItems).isEmpty()
    }

    @Test
    fun `san pham khong thuoc chuong trinh thi khong duoc giam`() {
        val lines = listOf(Fixtures.line(productId = "KHAC", qty = 100.0))

        val result = engine.calculate(lines, listOf(Fixtures.qtyTier(productIds = listOf("P1"))))

        assertThat(result.totalDiscount).isEqualTo(0L)
    }

    @Test
    fun `khong co chuong trinh nao thi don giu nguyen`() {
        val lines = listOf(Fixtures.line(qty = 100.0, unitPrice = 100_000))

        val result = engine.calculate(lines, emptyList())

        assertThat(result.totalDiscount).isEqualTo(0L)
    }

    @Test
    fun `chiet khau khong bao gio vuot qua gia tri don`() {
        val lines = listOf(Fixtures.line(qty = 10.0, unitPrice = 100_000))
        val huge = Fixtures.qtyTier(
            breaks = listOf(PromotionBreak("b1", 1, minQty = 1.0, discountPct = 5.0))
        )

        val result = engine.calculate(lines, listOf(huge))
        val totals = OrderCalculator.totals(lines, result)

        assertThat(totals.discountAmount).isAtMost(totals.subTotal)
        assertThat(totals.netAmount).isAtLeast(0L)
    }
}
