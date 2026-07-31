package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.StockCountLine
import com.tinhcd.esalessfa.domain.repository.StockCountRepository
import javax.inject.Inject

/** Phiếu kiểm kê lúc mở: cửa hàng đang ghé và các dòng cần đếm. */
data class StockCountSheet(
    val customer: Customer?,
    val lines: List<StockCountLine>,
)

/**
 * Nạp phiếu kiểm kê cho một cửa hàng.
 *
 * Danh sách dòng phải là của ĐÚNG cửa hàng đang mở — số gợi ý trong đó dựa trên
 * lịch sử bán tại chính cửa hàng đó, lấy nhầm nơi là gợi ý vô nghĩa.
 */
class LoadStockCountUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val stockCountRepository: StockCountRepository,
) {

    suspend operator fun invoke(customerId: String): StockCountSheet = StockCountSheet(
        customer = customerRepository.getById(customerId),
        lines = stockCountRepository.linesFor(customerId),
    )
}
