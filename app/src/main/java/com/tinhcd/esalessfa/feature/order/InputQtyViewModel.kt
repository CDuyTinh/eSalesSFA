package com.tinhcd.esalessfa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.ProductUom
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InputQtyUiState(
    val uoms: List<ProductUom> = emptyList(),
    val selectedUom: String = "",
    val unitPrice: Long = 0,
)

/**
 * Trạng thái của hộp thoại nhập số lượng.
 *
 * Giá đổi theo đơn vị (Thùng khác Lẻ) nên phải tra lại mỗi khi user đổi chip.
 * Việc tra giá và chọn đơn vị mặc định trước đây nằm ngay trong DialogFragment,
 * tức là hộp thoại tự cầm ProductRepository — cùng lỗi với các Fragment tự đọc
 * dữ liệu, chỉ nhỏ hơn.
 */
@HiltViewModel
class InputQtyViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId: String = savedStateHandle[ARG_PRODUCT_ID] ?: ""
    private val priceGroupId: String = savedStateHandle[ARG_PRICE_GROUP] ?: ""
    private val presetUom: String? = savedStateHandle[ARG_PRESET_UOM]

    private val _uiState = MutableStateFlow(InputQtyUiState())
    val uiState: StateFlow<InputQtyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val uoms = productRepository.getById(productId)?.uoms.orEmpty()

            // Ưu tiên đơn vị đang sửa, rồi tới đơn vị bán mặc định của sản phẩm.
            val initial = presetUom
                ?: uoms.firstOrNull { it.isDefaultSale }?.code
                ?: uoms.firstOrNull()?.code
                ?: ""

            _uiState.update { it.copy(uoms = uoms, selectedUom = initial) }
            if (initial.isNotEmpty()) loadPrice(initial)
        }
    }

    fun onUomSelected(code: String) {
        _uiState.update { it.copy(selectedUom = code) }
        viewModelScope.launch { loadPrice(code) }
    }

    private suspend fun loadPrice(uom: String) {
        val price = productRepository.getPrice(priceGroupId, productId, uom) ?: 0L
        _uiState.update { it.copy(unitPrice = price) }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_PRODUCT_NAME = "productName"
        const val ARG_PRESET_UOM = "presetUom"
        const val ARG_PRESET_QTY = "presetQty"
        const val ARG_PRICE_GROUP = "priceGroupId"
    }
}
