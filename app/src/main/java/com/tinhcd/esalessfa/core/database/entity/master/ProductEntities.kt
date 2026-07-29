package com.tinhcd.esalessfa.core.database.entity.master

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "product_categories")
data class ProductCategoryEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val parentId: String?,
    val sortOrder: Int,
)

@Entity(
    tableName = "products",
    indices = [
        Index("code", unique = true),
        Index("categoryId"),
        Index("barcode"),
        Index("nameSearch"),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val nameSearch: String?,
    val barcode: String?,
    val categoryId: String?,
    /** Đơn vị nhỏ nhất. Mọi conversionRate đều quy về đơn vị này. */
    val baseUom: String,
    val vatRate: Double,
    val imageUrl: String?,
    val isTrackStock: Boolean,
    val isActive: Boolean,
)

/**
 * Đơn vị tính và hệ số quy đổi của sản phẩm — 1 Thùng = 24 Lẻ.
 *
 * Đây là bảng gốc của mọi phép tính tiền và mọi so sánh số lượng trong engine
 * khuyến mãi. Điều kiện "mua 10" là 10 thùng hay 10 lẻ phụ thuộc hoàn toàn vào
 * việc quy đổi ở đây có đúng không.
 */
@Entity(
    tableName = "product_uoms",
    indices = [Index(value = ["productId", "uomCode"], unique = true)],
)
data class ProductUomEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val uomCode: String,
    val conversionRate: Double,
    val isDefaultSale: Boolean,
    val sortOrder: Int,
)

/**
 * Giá theo nhóm khách hàng × sản phẩm × đơn vị tính × khoảng ngày hiệu lực.
 *
 * Index gộp 3 cột đầu vì đây là truy vấn chạy liên tục — mỗi lần user đổi số
 * lượng trên màn đặt hàng đều phải tra lại giá.
 */
@Entity(
    tableName = "price_lists",
    indices = [Index(value = ["priceGroupId", "productId", "uomCode"])],
)
data class PriceListEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val priceGroupId: String,
    val uomCode: String,
    /** VND, không có phần thập phân. Dùng Double ở đây là sai lệch tiền. */
    val price: Long,
    /** "yyyy-MM-dd" — so sánh chuỗi vẫn đúng thứ tự nên không cần TypeConverter. */
    val fromDate: String,
    val toDate: String,
)
