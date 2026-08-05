package com.tinhcd.esalessfa.domain.model.stock

/** Một dòng kiểm kê hiển thị trên lưới nhập liệu. */
data class StockCountLine(
    val productId: String,
    val productCode: String,
    val productName: String,
    val uomCode: String,
    /** Tồn đếm được lần này. null = chưa nhập, khác 0 = đã đếm và bằng 0. */
    val qty: Double? = null,
    /** Tồn của lần kiểm kê gần nhất. */
    val prevQty: Double,
) {
    /**
     * Gợi ý số lượng cần đặt.
     *
     * Bằng lượng đã bán từ lần kiểm kê trước tới nay — giả định cửa hàng muốn
     * quay lại mức tồn cũ. Chỉ gợi ý khi đã nhập số, và không gợi ý số âm khi
     * cửa hàng nhập thêm hàng từ nguồn khác.
     */
    val suggestQty: Double
        get() = if (qty == null) 0.0 else (prevQty - qty).coerceAtLeast(0.0)

    val isCounted: Boolean get() = qty != null
}
