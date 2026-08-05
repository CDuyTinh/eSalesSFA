package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.model.order.PromotionResult

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
