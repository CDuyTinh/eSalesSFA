package com.tinhcd.esalessfa.domain.model.promotion

import com.google.common.truth.Truth.assertThat
import kotlin.system.measureTimeMillis
import org.junit.Test

/**
 * Engine chạy lại TOÀN BỘ pipeline mỗi lần user đổi số lượng một dòng — đó là
 * cái giá để không phải quản lý trạng thái tăng dần và tránh rác từ lần tính
 * trước. Cái giá đó chỉ chấp nhận được nếu một lượt tính đủ nhanh để gõ phím
 * không thấy khựng.
 */
class PromotionPerformanceTest {

    @Test
    fun `tinh lai don 200 dong duoi 50ms`() {
        val lines = (1..200).map { i ->
            Fixtures.line(
                lineNo = i,
                productId = "P${i % 50}",
                qty = (i % 30 + 1).toDouble(),
                unitPrice = 50_000L + i * 137,
                vatRate = 0.08,
            )
        }

        val programs = buildList {
            repeat(10) { i ->
                add(Fixtures.qtyTier(code = "KM$i", productIds = (0..49).map { "P$it" }, priority = i))
            }
            add(Fixtures.amountTier())
            add(Fixtures.freeItem(buyProductIds = (0..49).map { "P$it" }))
        }

        val engine = PromotionEngine.default()

        // Chạy nóng trước để loại thời gian JIT khỏi phép đo.
        repeat(5) { engine.calculate(lines, programs) }

        val elapsed = measureTimeMillis {
            repeat(10) { engine.calculate(lines, programs) }
        } / 10

        assertThat(engine.calculate(lines, programs).totalDiscount).isGreaterThan(0L)
        assertThat(elapsed).isLessThan(50L)
    }

    @Test
    fun `ket qua khong doi khi chay lai nhieu lan`() {
        val lines = (1..50).map { Fixtures.line(lineNo = it, qty = 20.0) }
        val programs = listOf(Fixtures.qtyTier(), Fixtures.amountTier(), Fixtures.freeItem())
        val engine = PromotionEngine.default()

        val first = engine.calculate(lines, programs)
        val second = engine.calculate(lines, programs)

        // Rule là hàm thuần nên hai lượt phải cho kết quả giống hệt. Nếu lệch
        // nghĩa là có trạng thái rò rỉ giữa các lần tính.
        assertThat(second.totalDiscount).isEqualTo(first.totalDiscount)
        assertThat(second.appliedProgramCodes).isEqualTo(first.appliedProgramCodes)
        assertThat(second.freeItems.map { it.qty }).isEqualTo(first.freeItems.map { it.qty })
    }
}
