package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.order.OrderCalculator
import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.model.order.OrderTotals
import com.tinhcd.esalessfa.domain.model.order.PromotionResult
import com.tinhcd.esalessfa.domain.model.promotion.PromotionEngine
import com.tinhcd.esalessfa.domain.model.promotion.PromotionProgram
import javax.inject.Inject

/** Khuyến mãi đã áp và tiền của một giỏ hàng, tính trong cùng một lượt. */
data class OrderCalculation(
    val promotion: PromotionResult,
    val totals: OrderTotals,
)

/**
 * Tính lại toàn bộ giỏ hàng: dựng pipeline khuyến mãi, chạy, rồi cộng tiền.
 *
 * Gom hai bước vào một chỗ vì chúng luôn đi cùng nhau và phải dùng CÙNG một
 * PromotionResult — tính khuyến mãi ở một nơi rồi cộng tiền ở nơi khác là cách
 * nhanh nhất để tổng tiền lệch với dòng chiết khấu.
 *
 * ViewModel vì vậy không còn tự dựng PromotionEngine: nó chỉ đưa giỏ hàng vào và
 * nhận kết quả, còn việc rule nào nằm trong pipeline là chuyện của domain.
 */
class CalculateOrderUseCase @Inject constructor() {

    operator fun invoke(
        lines: List<OrderLine>,
        programs: List<PromotionProgram>,
        manualDiscount: Long,
    ): OrderCalculation {
        val promotion = PromotionEngine.default(manualDiscount).calculate(lines, programs)
        return OrderCalculation(
            promotion = promotion,
            totals = OrderCalculator.totals(lines, promotion),
        )
    }
}
