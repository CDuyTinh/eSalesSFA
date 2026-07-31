package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromotionResult
import com.tinhcd.esalessfa.domain.repository.OrderRepository
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import com.tinhcd.esalessfa.domain.sync.SyncRun
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * "Chốt đơn xong phải xin đẩy lên" là quy tắc, không phải thói quen của màn hình.
 * Test này giữ cặp việc đó dính nhau.
 */
class ConfirmOrderUseCaseTest {

    private val line = OrderLine(
        lineNo = 1,
        productId = "SP001",
        uomCode = "THUNG",
        qty = 2.0,
        conversionRate = 24.0,
        unitPrice = 100_000,
        vatRate = 10.0,
    )

    @Test
    fun `chot don thanh cong thi kich day len va tra ma don`() = runTest {
        val orders = FakeOrderRepository(orderNo = "DH0001")
        val scheduler = CountingSyncScheduler()

        val orderNo = ConfirmOrderUseCase(orders, scheduler)(
            customerId = "KH001",
            lines = listOf(line),
            result = PromotionResult(),
            note = null,
        )

        assertThat(orderNo).isEqualTo("DH0001")
        assertThat(scheduler.uploadStarts).isEqualTo(1)
    }

    @Test
    fun `luu that bai thi KHONG kich day len`() = runTest {
        val orders = FakeOrderRepository(error = IllegalStateException("hết dung lượng"))
        val scheduler = CountingSyncScheduler()

        runCatching {
            ConfirmOrderUseCase(orders, scheduler)(
                customerId = "KH001",
                lines = listOf(line),
                result = PromotionResult(),
                note = null,
            )
        }

        assertThat(scheduler.uploadStarts).isEqualTo(0)
    }
}

private class FakeOrderRepository(
    private val orderNo: String = "DH0001",
    private val error: Throwable? = null,
) : OrderRepository {

    override suspend fun confirmOrder(
        customerId: String,
        lines: List<OrderLine>,
        result: PromotionResult,
        note: String?,
    ): String {
        error?.let { throw it }
        return orderNo
    }
}

private class CountingSyncScheduler : SyncScheduler {

    var uploadStarts = 0
        private set

    override fun startDownload(force: Boolean) = Unit

    override fun startUpload() {
        uploadStarts++
    }

    override fun startFullSync(): Flow<SyncRun> = flowOf(SyncRun())

    override fun observeDownload(): Flow<SyncRun> = flowOf(SyncRun())
}
