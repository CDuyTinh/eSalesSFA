package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.Product
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram
import com.tinhcd.esalessfa.domain.promotion.model.PromotionResult
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

interface PromotionRepository {
    /** Chương trình còn hiệu lực hôm nay, đã gom đủ bậc và sản phẩm. */
    suspend fun activePrograms(): List<PromotionProgram>
}

interface OrderRepository {

    /**
     * Lưu đơn vào Room với trạng thái PENDING để worker đẩy lên sau.
     *
     * Trả về mã đơn đã sinh. Đơn được ghi cả cụm (header + dòng + khuyến mãi)
     * trong một transaction.
     */
    suspend fun confirmOrder(
        customerId: String,
        lines: List<OrderLine>,
        result: PromotionResult,
        note: String?,
    ): String
}
