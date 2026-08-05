package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.order.FreeItem
import com.tinhcd.esalessfa.domain.model.promotion.PromotionProgram
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import com.tinhcd.esalessfa.domain.repository.PromotionRepository
import javax.inject.Inject

/** Mọi thứ màn đặt hàng cần trước khi nhận dòng hàng đầu tiên. */
data class OrderContext(
    val customer: Customer?,
    val programs: List<PromotionProgram>,
)

/** Một hàng tặng đã có tên đọc được, sẵn sàng hiển thị. */
data class FreeItemInfo(
    val productName: String,
    val qty: Double,
    val programCode: String,
)

/**
 * Nạp bối cảnh cho màn đặt hàng, cùng kiểu với [LoadCheckInContextUseCase].
 *
 * Khách hàng và danh sách chương trình khuyến mãi luôn đi cùng nhau: nhóm giá
 * của khách quyết định giá từng dòng, còn chương trình quyết định chiết khấu và
 * hàng tặng. Thiếu một trong hai thì mọi phép tính sau đó đều sai chứ không chỉ
 * hiển thị thiếu.
 */
class LoadOrderContextUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val promotionRepository: PromotionRepository,
) {

    suspend operator fun invoke(customerId: String): OrderContext = OrderContext(
        customer = customerRepository.getById(customerId),
        programs = promotionRepository.activePrograms(),
    )
}

/**
 * Đổi hàng tặng do engine khuyến mãi sinh ra thành thứ đọc được.
 *
 * Engine chỉ trả về id sản phẩm vì nó là hàm thuần, không chạm tới kho dữ liệu.
 * Sản phẩm biến mất khỏi master data sau một lượt đồng bộ thì hiện tạm mã thay
 * vì bỏ trống — dòng hàng tặng vẫn có thật và vẫn phải xuất hiện trên đơn.
 */
class DescribeFreeItemsUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {

    suspend operator fun invoke(items: List<FreeItem>): List<FreeItemInfo> = items.map { free ->
        FreeItemInfo(
            productName = productRepository.getById(free.productId)?.name ?: free.productId,
            qty = free.qty,
            programCode = free.programCode,
        )
    }
}
