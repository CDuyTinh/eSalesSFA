package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.CatalogRepository
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Những con số nhân viên nhìn đầu ngày: đã tải về gì, còn gì chờ gửi lên. */
data class WorkSummary(
    val salespersonName: String = "",
    val customerCount: Int = 0,
    val productCount: Int = 0,
    val promotionCount: Int = 0,
    val lastSyncedAt: Long? = null,
    val pendingCount: Int = 0,
)

/**
 * Gom bốn kho dữ liệu thành một bản tóm tắt cho màn Công việc.
 *
 * Mỗi nguồn là Flow từ Room nên khi sync ghi dữ liệu mới, màn hình tự cập nhật —
 * không cần gọi refresh thủ công sau khi đồng bộ xong.
 *
 * combine chỉ có overload giữ nguyên kiểu tới 5 luồng; nhiều hơn sẽ rơi vào bản
 * vararg trả về Array<Any?> và mất hết type safety. Vì vậy gộp theo hai nhóm.
 */
class ObserveWorkSummaryUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val catalogRepository: CatalogRepository,
    private val salespersonRepository: SalespersonRepository,
    private val syncRepository: SyncRepository,
) {

    operator fun invoke(): Flow<WorkSummary> {
        val counts = combine(
            customerRepository.observeCustomerCount(),
            catalogRepository.observeProductCount(),
            catalogRepository.observeActivePromotionCount(),
        ) { customers, products, promotions -> Triple(customers, products, promotions) }

        val syncInfo = combine(
            syncRepository.observeLastSyncedAt(),
            syncRepository.observePendingCount(),
        ) { lastSync, pending -> lastSync to pending }

        return combine(
            salespersonRepository.observeCurrent().map { it?.fullName.orEmpty() },
            counts,
            syncInfo,
        ) { name, (customers, products, promotions), (lastSync, pending) ->
            WorkSummary(
                salespersonName = name,
                customerCount = customers,
                productCount = products,
                promotionCount = promotions,
                lastSyncedAt = lastSync,
                pendingCount = pending,
            )
        }
    }
}
