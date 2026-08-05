package com.tinhcd.esalessfa.domain.model.order

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
