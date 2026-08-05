package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import javax.inject.Inject

sealed interface BuildOrderLineResult {

    /** Dòng hàng đã có đủ giá và quy đổi; [productName]/[productCode] để hiển thị. */
    data class Built(
        val line: OrderLine,
        val productName: String,
        val productCode: String,
    ) : BuildOrderLineResult

    /** Sản phẩm không còn trong master data đã tải về. */
    data object ProductNotFound : BuildOrderLineResult

    /** Không có giá cho nhóm giá của khách hàng này — không được tự bịa giá 0. */
    data object NoPrice : BuildOrderLineResult
}

/**
 * Dựng một dòng hàng từ sản phẩm, đơn vị tính và nhóm giá của khách.
 *
 * Giá và tỷ lệ quy đổi phải tra theo ĐÚNG cặp (nhóm giá, đơn vị): Thùng và Lẻ là
 * hai giá khác nhau, và quy đổi sai thì khuyến mãi theo số lượng tính lệch theo.
 * Trả về kiểu có nhánh thay vì null để nơi gọi buộc phải xử lý ca thiếu giá.
 */
class BuildOrderLineUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {

    suspend operator fun invoke(
        lineNo: Int,
        productId: String,
        uomCode: String,
        qty: Double,
        priceGroupId: String,
    ): BuildOrderLineResult {
        val product = productRepository.getById(productId)
            ?: return BuildOrderLineResult.ProductNotFound
        val uom = product.uoms.firstOrNull { it.code == uomCode }
            ?: return BuildOrderLineResult.ProductNotFound

        val price = productRepository.getPrice(priceGroupId, productId, uomCode)
            ?: return BuildOrderLineResult.NoPrice

        return BuildOrderLineResult.Built(
            line = OrderLine(
                lineNo = lineNo,
                productId = productId,
                uomCode = uomCode,
                qty = qty,
                conversionRate = uom.conversionRate,
                unitPrice = price,
                vatRate = product.vatRate,
            ),
            productName = product.name,
            productCode = product.code,
        )
    }
}
