package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.ProductUom
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import javax.inject.Inject

/** Các lựa chọn của hộp nhập số lượng, ứng với một đơn vị đang chọn. */
data class QtyOptions(
    val uoms: List<ProductUom> = emptyList(),
    val selectedUom: String = "",
    val unitPrice: Long = 0,
)

/**
 * Chọn đơn vị tính và tra giá cho hộp nhập số lượng.
 *
 * Hai quy tắc ở đây trước nằm trong ViewModel nên không test được nếu không
 * dựng Android:
 *
 * - Thứ tự ưu tiên đơn vị: đơn vị đang sửa dở, rồi tới đơn vị bán mặc định của
 *   sản phẩm. Chọn sai đơn vị là sai cả giá lẫn hệ số quy đổi, kéo theo khuyến
 *   mãi theo số lượng tính lệch. Phần "đơn vị mặc định là gì" hỏi thẳng
 *   [com.tinhcd.esalessfa.domain.model.Product.defaultUom] chứ không chép lại —
 *   hai nơi định nghĩa một luật thì sớm muộn cũng lệch nhau.
 * - Giá tra theo ĐÚNG cặp (nhóm giá, đơn vị): Thùng và Lẻ là hai giá khác nhau.
 *
 * Thiếu giá vẫn trả 0 chứ không chặn ở đây: hộp này chỉ để xem trước, còn việc
 * từ chối dòng hàng không có giá là của [BuildOrderLineUseCase] lúc bấm thêm.
 */
class LoadQtyOptionsUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {

    suspend operator fun invoke(
        productId: String,
        priceGroupId: String,
        preferredUom: String?,
    ): QtyOptions {
        val product = productRepository.getById(productId) ?: return QtyOptions()
        val uoms = product.uoms

        // Đơn vị mong muốn phải thật sự có trong sản phẩm: master data đồng bộ
        // lại có thể bỏ một đơn vị mà dòng hàng cũ vẫn đang trỏ tới.
        val selected = preferredUom?.takeIf { code -> uoms.any { it.code == code } }
            ?: product.defaultUom?.code
            ?: return QtyOptions(uoms = uoms)

        return QtyOptions(
            uoms = uoms,
            selectedUom = selected,
            unitPrice = productRepository.getPrice(priceGroupId, productId, selected) ?: 0L,
        )
    }
}
