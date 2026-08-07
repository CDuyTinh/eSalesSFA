package com.tinhcd.esalessfa.domain.repository

// PagingData nằm trong artifact paging-common — Kotlin thuần, không kéo theo
// Android SDK; xem ghi chú dài hơn ở CustomerRepository.
import androidx.paging.PagingData
import com.tinhcd.esalessfa.domain.model.product.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    /**
     * Sản phẩm để chọn vào đơn, phân trang và lọc theo [query].
     *
     * [query] là chuỗi đã bỏ dấu để khớp với cột nameSearch. Phải phân trang vì
     * danh mục của DMS thật có hàng nghìn SKU: đọc cả bảng lên RAM rồi lọc sẽ
     * giật ngay từ ký tự đầu user gõ.
     */
    fun pagedProducts(query: String): Flow<PagingData<Product>>

    suspend fun getById(id: String): Product?

    /**
     * Giá hiệu lực hôm nay cho nhóm giá của khách hàng.
     *
     * Trả null khi chưa cấu hình giá — khi đó KHÔNG được cho thêm vào đơn, vì
     * đơn giá 0 sẽ đi thẳng lên server và bị từ chối ở bước đối chiếu giá.
     */
    suspend fun getPrice(priceGroupId: String, productId: String, uomCode: String): Long?
}
