package com.tinhcd.esalessfa.domain.model.order

/**
 * Một dòng hàng đưa vào engine.
 *
 * Tiền dùng Long (VND, không thập phân). Dùng Double cho tiền sẽ tích luỹ sai số
 * qua hàng trăm dòng và làm tổng đơn lệch với server, khiến sync bị từ chối.
 */
data class OrderLine(
    val lineNo: Int,
    val productId: String,
    val uomCode: String,
    val qty: Double,
    /** Hệ số quy đổi về đơn vị nhỏ nhất. 1 Thùng = 24 Lẻ. */
    val conversionRate: Double,
    /** Đơn giá theo [uomCode]. */
    val unitPrice: Long,
    val vatRate: Double = 0.0,
) {
    /**
     * Số lượng quy về đơn vị gốc.
     *
     * Điều kiện khuyến mãi luôn so trên đơn vị này — nếu không, "mua 10" sẽ có
     * nghĩa khác nhau tuỳ user chọn Thùng hay Lẻ.
     */
    val baseQty: Double get() = qty * conversionRate

    /** Thành tiền trước chiết khấu. */
    val grossAmount: Long get() = MoneyMath.multiply(unitPrice, qty)
}

/** Chiết khấu áp lên một dòng cụ thể. */
data class LineDiscount(
    val lineNo: Int,
    val programId: String,
    val programCode: String,
    val breakId: String?,
    val amount: Long,
    /** Số suất được hưởng. */
    val applyTimes: Double = 1.0,
    val isManual: Boolean = false,
)

/** Hàng tặng do chương trình sinh ra. */
data class FreeItem(
    val productId: String,
    val qty: Double,
    val programId: String,
    val programCode: String,
    val breakId: String?,
)

/** Kết quả cuối cùng của engine. */
data class PromotionResult(
    val lineDiscounts: List<LineDiscount> = emptyList(),
    val freeItems: List<FreeItem> = emptyList(),
    /** Chương trình đã áp, phục vụ hiển thị và đối chiếu với server. */
    val appliedProgramCodes: List<String> = emptyList(),
) {
    val totalDiscount: Long get() = lineDiscounts.sumOf { it.amount }

    fun discountForLine(lineNo: Int): Long =
        lineDiscounts.filter { it.lineNo == lineNo }.sumOf { it.amount }
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
