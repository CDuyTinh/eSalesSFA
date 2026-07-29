package com.tinhcd.esalessfa.domain.promotion.rule

import com.tinhcd.esalessfa.domain.promotion.model.FreeItem
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram

/**
 * Mua X tặng Y.
 *
 * Ba trần lần lượt cắt số lượng tặng, và thứ tự áp trần rất quan trọng:
 *   1. Số suất  = floor(số lượng mua / điều kiện), có thể bị maxApplyTimes chặn
 *   2. Tồn kho hàng tặng — hết hàng thì không tặng được dù đủ điều kiện
 *   3. Ngân sách chương trình
 *
 * Bỏ qua bước 2 là hứa tặng thứ không có trong kho, nhân viên ra tới cửa hàng
 * mới phát hiện.
 */
class FreeItemRule : PromotionRule {

    override fun supports(program: PromotionProgram) = program.type == PromoType.FREE_ITEM

    override fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome {
        val eligible = eligibleLines(program, context)
        if (eligible.isEmpty() || program.freeItems.isEmpty()) return RuleOutcome.NONE

        val totalQty = eligible.sumOf { it.second }
        val breaks = selectBreaks(program, totalQty, eligible.sumOf { it.first.grossAmount })
        if (breaks.isEmpty()) return RuleOutcome.NONE

        val freeItems = mutableListOf<FreeItem>()

        // Tồn kho hàng tặng là tài nguyên dùng chung giữa các bậc -> theo dõi
        // phần còn lại trong suốt vòng lặp.
        val remainingStock = program.freeItems
            .associate { it.productId to (it.freeStockQty ?: Double.MAX_VALUE) }
            .toMutableMap()

        for (br in breaks) {
            val times = applyTimes(totalQty, br.minQty, br.maxApplyTimes)
            if (times <= 0.0 || br.freeQty <= 0.0) continue

            val wantedQty = times * br.freeQty

            for (item in program.freeItems) {
                val available = remainingStock[item.productId] ?: 0.0
                val granted = minOf(wantedQty, available)
                if (granted <= 0.0) continue

                freeItems += FreeItem(
                    productId = item.productId,
                    qty = granted,
                    programId = program.id,
                    programCode = program.code,
                    breakId = br.id,
                )
                remainingStock[item.productId] = available - granted
            }
        }

        if (freeItems.isEmpty()) return RuleOutcome.NONE

        val consumed = eligible.associate { (line, qty) -> line.lineNo to qty }
        return RuleOutcome(emptyList(), freeItems, consumed)
    }
}
