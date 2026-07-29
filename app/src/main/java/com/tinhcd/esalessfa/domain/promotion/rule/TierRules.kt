package com.tinhcd.esalessfa.domain.promotion.rule

import com.tinhcd.esalessfa.domain.promotion.model.ApplyLevel
import com.tinhcd.esalessfa.domain.promotion.model.DiscountKind
import com.tinhcd.esalessfa.domain.promotion.model.LineDiscount
import com.tinhcd.esalessfa.domain.promotion.model.MoneyMath
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram
import kotlin.math.floor

/**
 * Chiết khấu bậc theo SỐ LƯỢNG: mua ≥10 giảm 3%, ≥20 giảm 5%, ≥50 giảm 8%.
 *
 * Điều kiện so trên baseQty (đơn vị nhỏ nhất), không phải qty người dùng nhập —
 * nếu không thì "mua 10" sẽ khác nhau tuỳ user chọn Thùng hay Lẻ.
 */
class QuantityTierRule : PromotionRule {

    override fun supports(program: PromotionProgram) = program.type == PromoType.QTY_TIER

    override fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome {
        val eligible = eligibleLines(program, context)
        if (eligible.isEmpty()) return RuleOutcome.NONE

        return when (program.applyLevel) {
            ApplyLevel.LINE -> applyPerLine(program, eligible, context)
            ApplyLevel.GROUP, ApplyLevel.DOCUMENT -> applyOnGroup(program, eligible, context)
        }
    }

    /** Mỗi dòng tự xét điều kiện riêng. */
    private fun applyPerLine(
        program: PromotionProgram,
        eligible: List<Pair<com.tinhcd.esalessfa.domain.promotion.model.OrderLine, Double>>,
        context: RuleContext,
    ): RuleOutcome {
        val discounts = mutableListOf<LineDiscount>()
        val consumed = mutableMapOf<Int, Double>()
        var budget = context.remainingBudget

        for ((line, availableQty) in eligible) {
            val breaks = selectBreaks(program, availableQty, line.grossAmount)
            if (breaks.isEmpty()) continue

            var lineAmount = 0L
            var lastBreakId: String? = null

            for (br in breaks) {
                val raw = when (program.discountKind) {
                    DiscountKind.PERCENT -> MoneyMath.percentOf(line.grossAmount, br.discountPct)
                    // Tiền tặng tính theo SỐ SUẤT, không phải một lần.
                    DiscountKind.AMOUNT -> {
                        val times = applyTimes(availableQty, br.minQty, br.maxApplyTimes)
                        MoneyMath.multiply(br.discountAmount, times)
                    }
                    DiscountKind.FREE_ITEM -> 0L
                }
                lineAmount += raw
                lastBreakId = br.id
            }

            val capped = capByBudget(lineAmount, budget)
            if (capped <= 0L) continue

            discounts += LineDiscount(
                lineNo = line.lineNo,
                programId = program.id,
                programCode = program.code,
                breakId = lastBreakId,
                amount = capped,
            )
            consumed[line.lineNo] = availableQty
            budget = budget?.minus(capped)
        }

        return RuleOutcome(discounts, emptyList(), consumed)
    }

    /** Gộp số lượng mọi dòng rồi xét một lần, chiết khấu phân bổ ngược lại. */
    private fun applyOnGroup(
        program: PromotionProgram,
        eligible: List<Pair<com.tinhcd.esalessfa.domain.promotion.model.OrderLine, Double>>,
        context: RuleContext,
    ): RuleOutcome {
        val totalQty = eligible.sumOf { it.second }
        val totalAmount = eligible.sumOf { it.first.grossAmount }

        val breaks = selectBreaks(program, totalQty, totalAmount)
        if (breaks.isEmpty()) return RuleOutcome.NONE

        val rawDiscount = breaks.sumOf { br ->
            when (program.discountKind) {
                DiscountKind.PERCENT -> MoneyMath.percentOf(totalAmount, br.discountPct)
                DiscountKind.AMOUNT -> {
                    val times = applyTimes(totalQty, br.minQty, br.maxApplyTimes)
                    MoneyMath.multiply(br.discountAmount, times)
                }
                DiscountKind.FREE_ITEM -> 0L
            }
        }

        val total = capByBudget(rawDiscount, context.remainingBudget)
        if (total <= 0L) return RuleOutcome.NONE

        return allocateToLines(program, eligible, total, breaks.last().id)
    }
}

/**
 * Chiết khấu bậc theo GIÁ TRỊ: đơn ≥5tr giảm 3%, ≥10tr giảm 5%.
 *
 * Thường dùng ở mức DOCUMENT (toàn đơn) và tính trên TẤT CẢ các dòng, không chỉ
 * dòng thuộc danh sách sản phẩm của chương trình.
 */
class AmountTierRule : PromotionRule {

    override fun supports(program: PromotionProgram) = program.type == PromoType.AMOUNT_TIER

    override fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome {
        // Không khai báo sản phẩm nào -> áp cho toàn bộ đơn.
        val eligible = if (program.buyProductIds.isEmpty()) {
            context.lines.map { it to it.baseQty }
        } else {
            eligibleLines(program, context)
        }
        if (eligible.isEmpty()) return RuleOutcome.NONE

        val totalAmount = eligible.sumOf { it.first.grossAmount }
        val totalQty = eligible.sumOf { it.second }

        val breaks = selectBreaks(program, totalQty, totalAmount)
        if (breaks.isEmpty()) return RuleOutcome.NONE

        val rawDiscount = breaks.sumOf { br ->
            when (program.discountKind) {
                DiscountKind.PERCENT -> MoneyMath.percentOf(totalAmount, br.discountPct)
                DiscountKind.AMOUNT -> br.discountAmount
                DiscountKind.FREE_ITEM -> 0L
            }
        }

        val total = capByBudget(rawDiscount, context.remainingBudget)
        if (total <= 0L) return RuleOutcome.NONE

        return allocateToLines(program, eligible, total, breaks.last().id)
    }
}

// =============================================================================

/** Số suất = phần nguyên của (số lượng / điều kiện), có thể bị chặn trần. */
internal fun applyTimes(qty: Double, minQty: Double?, maxTimes: Int?): Double {
    if (minQty == null || minQty <= 0.0) return 1.0
    val times = floor(qty / minQty)
    return if (maxTimes != null) minOf(times, maxTimes.toDouble()) else times
}

/**
 * Phân bổ chiết khấu nhóm/toàn đơn ngược về từng dòng theo tỉ lệ thành tiền.
 *
 * Dùng [MoneyMath.allocate] để tổng phân bổ khớp chính xác với chiết khấu đã
 * tính; làm tròn từng dòng độc lập sẽ lệch vài đồng và server từ chối đơn.
 */
internal fun allocateToLines(
    program: PromotionProgram,
    eligible: List<Pair<com.tinhcd.esalessfa.domain.promotion.model.OrderLine, Double>>,
    totalDiscount: Long,
    breakId: String?,
): RuleOutcome {
    val weights = eligible.map { it.first.grossAmount }
    val parts = MoneyMath.allocate(totalDiscount, weights)

    val discounts = eligible.mapIndexedNotNull { index, (line, _) ->
        val amount = parts[index]
        if (amount <= 0L) null
        else LineDiscount(
            lineNo = line.lineNo,
            programId = program.id,
            programCode = program.code,
            breakId = breakId,
            amount = amount,
        )
    }

    val consumed = eligible.associate { (line, qty) -> line.lineNo to qty }
    return RuleOutcome(discounts, emptyList(), consumed)
}
