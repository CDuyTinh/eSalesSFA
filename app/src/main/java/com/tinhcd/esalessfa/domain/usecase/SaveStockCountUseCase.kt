package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.StockCountLine
import com.tinhcd.esalessfa.domain.repository.StockCountRepository
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import javax.inject.Inject

/**
 * Lưu phiếu kiểm kê rồi xin đẩy lên server — cùng cặp việc như chốt đơn.
 *
 * Xem [ConfirmOrderUseCase] để biết vì sao hai bước này không tách rời.
 */
class SaveStockCountUseCase @Inject constructor(
    private val stockCountRepository: StockCountRepository,
    private val syncScheduler: SyncScheduler,
) {

    suspend operator fun invoke(customerId: String, lines: List<StockCountLine>, note: String?) {
        stockCountRepository.save(customerId, lines, note)
        syncScheduler.startUpload()
    }
}
