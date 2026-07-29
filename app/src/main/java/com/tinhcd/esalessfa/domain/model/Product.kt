package com.tinhcd.esalessfa.domain.model

/** Đơn vị bán kèm hệ số quy đổi về đơn vị gốc. */
data class ProductUom(
    val code: String,
    val conversionRate: Double,
    val isDefaultSale: Boolean,
)

data class Product(
    val id: String,
    val code: String,
    val name: String,
    val baseUom: String,
    val vatRate: Double,
    val imageUrl: String?,
    val uoms: List<ProductUom> = emptyList(),
) {
    /**
     * Đơn vị mở sẵn khi thêm vào đơn.
     *
     * Ưu tiên đơn vị được đánh dấu bán mặc định (thường là Thùng) — nhân viên
     * bán sỉ hiếm khi nhập theo lẻ, bắt họ đổi mỗi lần là thừa thao tác.
     */
    val defaultUom: ProductUom?
        get() = uoms.firstOrNull { it.isDefaultSale }
            ?: uoms.maxByOrNull { it.conversionRate }
}
