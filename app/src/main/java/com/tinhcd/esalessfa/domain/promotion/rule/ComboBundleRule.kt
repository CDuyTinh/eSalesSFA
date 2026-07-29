package com.tinhcd.esalessfa.domain.promotion.rule

import com.tinhcd.esalessfa.domain.promotion.model.DiscountKind
import com.tinhcd.esalessfa.domain.promotion.model.MoneyMath
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram
import kotlin.math.floor

/**
 * Combo bộ: phải mua đủ MỌI chân của bộ mới được hưởng.
 *
 * Số bộ hoàn chỉnh = min(số bộ mà từng chân đáp ứng được). Thiếu một chân là
 * min = 0 và cả chương trình không áp — đây chính là điểm khác biệt so với
 * khuyến mãi nhóm thông thường, và cũng là chỗ hay bị làm sai thành "có chân
 * nào tính chân đó".
 */
class ComboBundleRule : PromotionRule {

    override fun supports(program: PromotionProgram) = program.type == PromoType.COMBO_BUNDLE

    override fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome {
        if (program.bundleGroups.isEmpty()) return RuleOutcome.NONE

        val eligible = eligibleLines(program, context)
        if (eligible.isEmpty()) return RuleOutcome.NONE

        val qtyByProduct = eligible
            .groupBy { it.first.productId }
            .mapValues { (_, pairs) -> pairs.sumOf { it.second } }

        // Với mỗi chân: số bộ mà chân đó gánh được.
        val setsPerGroup = program.bundleGroups.map { (_, items) ->
            val availableInGroup = items.sumOf { item -> qtyByProduct[item.productId] ?: 0.0 }
            val requiredPerSet = items.minOf { it.requiredQty }.takeIf { it > 0.0 } ?: 1.0
            floor(availableInGroup / requiredPerSet)
        }

        // Thiếu bất kỳ chân nào -> min = 0 -> cả bộ không tính.
        val completeSets = setsPerGroup.minOrNull() ?: 0.0
        if (completeSets <= 0.0) return RuleOutcome.NONE

        val breaks = selectBreaks(program, completeSets, eligible.sumOf { it.first.grossAmount })
        if (breaks.isEmpty()) return RuleOutcome.NONE

        val comboAmount = eligible.sumOf { it.first.grossAmount }

        val rawDiscount = breaks.sumOf { br ->
            when (program.discountKind) {
                DiscountKind.PERCENT -> MoneyMath.percentOf(comboAmount, br.discountPct)
                // Tiền tặng nhân theo SỐ BỘ, không phải một lần cho cả đơn.
                DiscountKind.AMOUNT -> MoneyMath.multiply(br.discountAmount, completeSets)
                DiscountKind.FREE_ITEM -> 0L
            }
        }

        val total = capByBudget(rawDiscount, context.remainingBudget)
        if (total <= 0L) return RuleOutcome.NONE

        return allocateToLines(program, eligible, total, breaks.last().id)
    }
}

/**
 * Chiết khấu tay do nhân viên nhập.
 *
 * Engine chỉ chịu trách nhiệm CHẶN TRẦN: số tiền vượt quá tỉ lệ cho phép sẽ bị
 * cắt xuống đúng trần thay vì từ chối, để nhân viên vẫn chốt được đơn.
 */
class ManualDiscountRule(
    private val requestedAmount: Long,
) : PromotionRule {

    override fun supports(program: PromotionProgram) = program.type == PromoType.MANUAL

    override fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome {
        if (requestedAmount <= 0L || context.lines.isEmpty()) return RuleOutcome.NONE

        val orderAmount = context.lines.sumOf { it.grossAmount }
        val maxPct = program.breaks.maxOfOrNull { it.discountPct } ?: 0.0
        val ceiling = MoneyMath.percentOf(orderAmount, maxPct)

        val amount = capByBudget(minOf(requestedAmount, ceiling), context.remainingBudget)
        if (amount <= 0L) return RuleOutcome.NONE

        val eligible = context.lines.map { it to it.baseQty }
        return allocateToLines(program, eligible, amount, program.breaks.lastOrNull()?.id)
            .let { outcome ->
                outcome.copy(
                    lineDiscounts = outcome.lineDiscounts.map { it.copy(isManual = true) },
                    // Chiết khấu tay không "tiêu" số lượng: nó không được chặn
                    // các chương trình tự động khác áp lên cùng dòng hàng.
                    consumedQty = emptyMap(),
                )
            }
    }
}
