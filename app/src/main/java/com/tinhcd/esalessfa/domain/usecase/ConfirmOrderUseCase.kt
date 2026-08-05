package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.model.order.PromotionResult
import com.tinhcd.esalessfa.domain.repository.OrderRepository
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import javax.inject.Inject

/**
 * Chốt đơn rồi xin đẩy lên server.
 *
 * Hai việc đi liền: có mạng thì đơn lên trong vài giây, không mạng thì hàng đợi
 * giữ lại và tự chạy khi kết nối trở lại — nhân viên không phải nhớ bấm đồng bộ.
 * Ghép ở đây để mọi nơi chốt đơn đều đẩy, không phụ thuộc màn hình nào nhớ gọi.
 */
class ConfirmOrderUseCase @Inject constructor(
    private val orderRepository: OrderRepository,
    private val syncScheduler: SyncScheduler,
) {

    suspend operator fun invoke(
        customerId: String,
        lines: List<OrderLine>,
        result: PromotionResult,
        note: String?,
    ): String {
        val orderNo = orderRepository.confirmOrder(
            customerId = customerId,
            lines = lines,
            result = result,
            note = note,
        )
        syncScheduler.startUpload()
        return orderNo
    }
}
