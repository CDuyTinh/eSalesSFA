package com.tinhcd.esalessfa.domain.promotion.model

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
 * Phép tính tiền tệ.
 *
 * Mọi phép nhân/chia đều đi qua BigDecimal rồi làm tròn HALF_UP về Long. Tính
 * trực tiếp trên Double sẽ cho 0.1 + 0.2 != 0.3 và lệch dần theo số dòng.
 */
object MoneyMath {

    fun multiply(amount: Long, factor: Double): Long =
        java.math.BigDecimal.valueOf(amount)
            .multiply(java.math.BigDecimal.valueOf(factor))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toLong()

    /** [pct] dạng 0.05 = 5%. */
    fun percentOf(amount: Long, pct: Double): Long = multiply(amount, pct)

    /**
     * Phân bổ [total] cho các dòng theo tỉ lệ [weights].
     *
     * Dòng cuối nhận phần dư để TỔNG PHÂN BỔ LUÔN BẰNG ĐÚNG [total]. Nếu làm
     * tròn từng dòng độc lập, tổng sẽ lệch vài đồng so với chiết khấu toàn đơn —
     * và server sẽ từ chối đơn vì số không khớp.
     */
    fun allocate(total: Long, weights: List<Long>): List<Long> {
        val sum = weights.sum()
        if (sum <= 0L || weights.isEmpty()) return weights.map { 0L }

        val allocated = weights.map { weight ->
            java.math.BigDecimal.valueOf(total)
                .multiply(java.math.BigDecimal.valueOf(weight))
                .divide(java.math.BigDecimal.valueOf(sum), 0, java.math.RoundingMode.DOWN)
                .toLong()
        }.toMutableList()

        val remainder = total - allocated.sum()
        val lastNonZero = weights.indexOfLast { it > 0 }
        if (lastNonZero >= 0) allocated[lastNonZero] += remainder

        return allocated
    }
}
