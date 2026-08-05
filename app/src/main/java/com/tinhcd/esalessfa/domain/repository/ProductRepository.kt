package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.product.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    fun search(query: String): Flow<List<Product>>

    suspend fun getById(id: String): Product?

    /**
     * Giá hiệu lực hôm nay cho nhóm giá của khách hàng.
     *
     * Trả null khi chưa cấu hình giá — khi đó KHÔNG được cho thêm vào đơn, vì
     * đơn giá 0 sẽ đi thẳng lên server và bị từ chối ở bước đối chiếu giá.
     */
    suspend fun getPrice(priceGroupId: String, productId: String, uomCode: String): Long?
}
