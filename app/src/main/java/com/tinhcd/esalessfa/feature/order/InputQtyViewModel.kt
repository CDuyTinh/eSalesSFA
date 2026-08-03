package com.tinhcd.esalessfa.feature.order

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.ProductUom
import com.tinhcd.esalessfa.domain.usecase.LoadQtyOptionsUseCase
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
 * Việc chọn đơn vị mặc định và tra giá nằm ở [LoadQtyOptionsUseCase] — đó là
 * quy tắc nghiệp vụ, để trong ViewModel thì chỉ test được bằng cách dựng Android.
 */
@HiltViewModel
class InputQtyViewModel @Inject constructor(
    private val loadQtyOptions: LoadQtyOptionsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val productId: String = savedStateHandle[ARG_PRODUCT_ID] ?: ""
    private val priceGroupId: String = savedStateHandle[ARG_PRICE_GROUP] ?: ""
    private val presetUom: String? = savedStateHandle[ARG_PRESET_UOM]

    private val _uiState = MutableStateFlow(InputQtyUiState())
    val uiState: StateFlow<InputQtyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val options = loadQtyOptions(productId, priceGroupId, presetUom)
            _uiState.update {
                it.copy(
                    uoms = options.uoms,
                    selectedUom = options.selectedUom,
                    unitPrice = options.unitPrice,
                )
            }
        }
    }

    /**
     * Đổi chip đơn vị hiện ngay, giá điền vào sau.
     *
     * Đợi tra xong giá rồi mới đổi chip thì người dùng bấm mà chip đứng im một
     * nhịp, tưởng máy không nhận.
     */
    fun onUomSelected(code: String) {
        _uiState.update { it.copy(selectedUom = code) }
        viewModelScope.launch {
            val options = loadQtyOptions(productId, priceGroupId, code)
            _uiState.update { it.copy(unitPrice = options.unitPrice) }
        }
    }

    companion object {
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_PRODUCT_NAME = "productName"
        const val ARG_PRESET_UOM = "presetUom"
        const val ARG_PRESET_QTY = "presetQty"
        const val ARG_PRICE_GROUP = "priceGroupId"
    }
}
