package com.tinhcd.esalessfa.domain.promotion

import com.tinhcd.esalessfa.domain.promotion.model.FreeItem
import com.tinhcd.esalessfa.domain.promotion.model.LineDiscount
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram
import com.tinhcd.esalessfa.domain.promotion.model.PromotionResult
import com.tinhcd.esalessfa.domain.promotion.rule.AmountTierRule
import com.tinhcd.esalessfa.domain.promotion.rule.ComboBundleRule
import com.tinhcd.esalessfa.domain.promotion.rule.FreeItemRule
import com.tinhcd.esalessfa.domain.promotion.rule.ManualDiscountRule
import com.tinhcd.esalessfa.domain.promotion.rule.PromotionRule
import com.tinhcd.esalessfa.domain.promotion.rule.QuantityTierRule
import com.tinhcd.esalessfa.domain.promotion.rule.RuleContext

/**
 * Chạy các chương trình khuyến mãi theo thứ tự ưu tiên (Chain of Responsibility).
 *
 * Engine không biết chi tiết bất kỳ loại khuyến mãi nào — nó chỉ chọn rule phù
 * hợp, truyền ngữ cảnh, rồi tích luỹ kết quả. Thêm loại mới là thêm một
 * [PromotionRule], không sửa file này.
 *
 * Đây là Kotlin thuần, không phụ thuộc Android: test chạy trên JVM trong vài
 * mili-giây, và đổi UI từ XML sang Compose không đụng tới nó.
 */
class PromotionEngine(
    private val rules: List<PromotionRule>,
) {

    fun calculate(
        lines: List<OrderLine>,
        programs: List<PromotionProgram>,
    ): PromotionResult {
        if (lines.isEmpty()) return PromotionResult()

        val allDiscounts = mutableListOf<LineDiscount>()
        val allFreeItems = mutableListOf<FreeItem>()
        val appliedCodes = mutableListOf<String>()

        // programCode -> (lineNo -> baseQty đã dùng). Chương trình sau đọc bảng
        // này để trừ phần đã bị chương trình bị loại trừ tiêu thụ.
        val consumedByProgram = mutableMapOf<String, Map<Int, Double>>()

        // Ngân sách phải theo dõi xuyên suốt: một chương trình có thể áp cho
        // nhiều dòng, và trần là tổng chứ không phải từng dòng.
        val remainingBudget = programs
            .filter { it.remainingBudget != null }
            .associate { it.code to it.remainingBudget!! }
            .toMutableMap()

        for (program in programs.sortedBy { it.priority }) {
            val rule = rules.firstOrNull { it.supports(program) } ?: continue

            val context = RuleContext(
                lines = lines,
                consumedQtyByProgram = consumedByProgram,
                remainingBudget = remainingBudget[program.code],
            )

            val outcome = rule.apply(program, context)
            if (!outcome.isApplied) continue

            allDiscounts += outcome.lineDiscounts
            allFreeItems += outcome.freeItems
            appliedCodes += program.code
            consumedByProgram[program.code] = outcome.consumedQty

            remainingBudget[program.code]?.let { budget ->
                remainingBudget[program.code] = (budget - outcome.totalDiscount).coerceAtLeast(0)
            }
        }

        return PromotionResult(
            lineDiscounts = allDiscounts,
            freeItems = allFreeItems,
            appliedProgramCodes = appliedCodes,
        )
    }

    companion object {

        /**
         * Bộ rule mặc định.
         *
         * [manualDiscountAmount] là số tiền nhân viên nhập tay; truyền 0 khi
         * không có. Rule chiết khấu tay chạy cuối vì nó tính trên tổng đơn.
         */
        fun default(manualDiscountAmount: Long = 0L) = PromotionEngine(
            listOf(
                QuantityTierRule(),
                AmountTierRule(),
                FreeItemRule(),
                ComboBundleRule(),
                ManualDiscountRule(manualDiscountAmount),
            )
        )
    }
}

/**
 * Tổng hợp đơn hàng sau khi đã có kết quả khuyến mãi.
 *
 * VAT tính trên số tiền SAU chiết khấu — tính trước sẽ khiến khách phải trả thuế
 * cho phần được giảm.
 */
data class OrderTotals(
    val subTotal: Long,
    val discountAmount: Long,
    val netAmount: Long,
    val vatAmount: Long,
    val totalAmount: Long,
)

object OrderCalculator {

    fun totals(lines: List<OrderLine>, result: PromotionResult): OrderTotals {
        val subTotal = lines.sumOf { it.grossAmount }
        val discount = result.totalDiscount.coerceAtMost(subTotal)

        val vat = lines.sumOf { line ->
            val lineNet = line.grossAmount - result.discountForLine(line.lineNo)
            com.tinhcd.esalessfa.domain.promotion.model.MoneyMath
                .percentOf(lineNet.coerceAtLeast(0), line.vatRate)
        }

        val net = subTotal - discount
        return OrderTotals(
            subTotal = subTotal,
            discountAmount = discount,
            netAmount = net,
            vatAmount = vat,
            totalAmount = net + vat,
        )
    }
}
