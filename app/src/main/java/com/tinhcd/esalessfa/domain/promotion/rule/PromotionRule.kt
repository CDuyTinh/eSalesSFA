package com.tinhcd.esalessfa.domain.promotion.rule

import com.tinhcd.esalessfa.domain.promotion.model.FreeItem
import com.tinhcd.esalessfa.domain.promotion.model.LineDiscount
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromotionBreak
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram

/**
 * Ngữ cảnh mà engine truyền cho từng rule.
 *
 * [consumedQtyByProgram] ghi nhận số lượng đã bị các chương trình trước dùng,
 * để rule hiện tại trừ ra khi chương trình của nó có khai báo loại trừ.
 */
data class RuleContext(
    val lines: List<OrderLine>,
    /** programCode -> (lineNo -> baseQty đã dùng) */
    val consumedQtyByProgram: Map<String, Map<Int, Double>>,
    /** Ngân sách còn lại của chương trình; null = không giới hạn. */
    val remainingBudget: Long?,
)

/** Kết quả một rule trả về. Rỗng nghĩa là chương trình không áp được. */
data class RuleOutcome(
    val lineDiscounts: List<LineDiscount> = emptyList(),
    val freeItems: List<FreeItem> = emptyList(),
    /** lineNo -> baseQty mà chương trình này đã dùng. */
    val consumedQty: Map<Int, Double> = emptyMap(),
) {
    val isApplied: Boolean get() = lineDiscounts.isNotEmpty() || freeItems.isNotEmpty()
    val totalDiscount: Long get() = lineDiscounts.sumOf { it.amount }

    companion object {
        val NONE = RuleOutcome()
    }
}

/**
 * Một chiến lược tính khuyến mãi (Strategy pattern).
 *
 * Rule là hàm thuần: cùng đầu vào luôn cho cùng đầu ra, không đọc/ghi trạng thái
 * bên ngoài. Nhờ vậy test được từng loại độc lập, và engine có thể chạy lại toàn
 * bộ pipeline mỗi khi user đổi số lượng mà không sợ rác từ lần tính trước.
 */
interface PromotionRule {

    /** Rule này xử lý được chương trình nào. */
    fun supports(program: PromotionProgram): Boolean

    fun apply(program: PromotionProgram, context: RuleContext): RuleOutcome
}

// =============================================================================
// Tiện ích dùng chung
// =============================================================================

/** Các dòng thuộc diện mua của chương trình, đã trừ phần bị loại trừ. */
internal fun eligibleLines(
    program: PromotionProgram,
    context: RuleContext,
): List<Pair<OrderLine, Double>> {
    if (program.buyProductIds.isEmpty()) return emptyList()

    return context.lines
        .filter { it.productId in program.buyProductIds }
        .map { line ->
            // Trừ số lượng đã bị các chương trình trong danh sách loại trừ dùng.
            val consumed = program.excludeProgramCodes.sumOf { code ->
                context.consumedQtyByProgram[code]?.get(line.lineNo) ?: 0.0
            }
            line to (line.baseQty - consumed).coerceAtLeast(0.0)
        }
        .filter { (_, availableQty) -> availableQty > 0.0 }
}

/**
 * Chọn bậc áp dụng.
 *
 * isMultiLevel = false -> chỉ bậc CAO NHẤT thoả điều kiện.
 * isMultiLevel = true  -> mọi bậc thoả, cộng dồn (tích luỹ bậc thang).
 */
internal fun selectBreaks(
    program: PromotionProgram,
    totalQty: Double,
    totalAmount: Long,
): List<PromotionBreak> {
    val matched = program.breaks
        .filter { br ->
            val qtyOk = br.minQty?.let { totalQty >= it } ?: true
            val amtOk = br.minAmount?.let { totalAmount >= it } ?: true
            qtyOk && amtOk
        }
        .sortedBy { it.level }

    if (matched.isEmpty()) return emptyList()
    return if (program.isMultiLevel) matched else listOf(matched.last())
}

/** Giới hạn chiết khấu theo ngân sách còn lại. */
internal fun capByBudget(amount: Long, remainingBudget: Long?): Long =
    if (remainingBudget == null) amount else minOf(amount, remainingBudget.coerceAtLeast(0))
