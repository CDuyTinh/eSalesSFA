package com.tinhcd.esalessfa.domain.model.promotion

import com.tinhcd.esalessfa.domain.model.order.FreeItem
import com.tinhcd.esalessfa.domain.model.order.LineDiscount
import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.model.order.PromotionResult

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
