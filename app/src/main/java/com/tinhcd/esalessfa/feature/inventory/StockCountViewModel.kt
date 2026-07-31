package com.tinhcd.esalessfa.feature.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.StockCountLine
import com.tinhcd.esalessfa.domain.usecase.LoadStockCountUseCase
import com.tinhcd.esalessfa.domain.usecase.SaveStockCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StockCountUiState(
    val customer: Customer? = null,
    val lines: List<StockCountLine> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
) {
    val countedCount: Int get() = lines.count { it.isCounted }
    val totalCount: Int get() = lines.size
    val suggestTotal: Double get() = lines.sumOf { it.suggestQty }
    val canSave: Boolean get() = countedCount > 0 && !isSaving
}

sealed interface StockCountEvent {
    data object Saved : StockCountEvent
    data class Error(val message: String) : StockCountEvent
}

@HiltViewModel
class StockCountViewModel @Inject constructor(
    private val loadStockCount: LoadStockCountUseCase,
    private val saveStockCount: SaveStockCountUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[ARG_CUSTOMER_ID] ?: ""

    private val _uiState = MutableStateFlow(StockCountUiState())
    val uiState: StateFlow<StockCountUiState> = _uiState.asStateFlow()

    private val _events = Channel<StockCountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val sheet = loadStockCount(customerId)
            _uiState.update {
                it.copy(customer = sheet.customer, lines = sheet.lines, isLoading = false)
            }
        }
    }

    /**
     * Nguồn sự thật của số lượng nằm ở đây, không nằm trong EditText.
     *
     * Nhờ vậy cuộn qua rồi quay lại vẫn thấy đúng số đã nhập, và xoay máy cũng
     * không mất dữ liệu đang gõ dở.
     */
    fun onQtyChanged(productId: String, qty: Double?) {
        _uiState.update { state ->
            state.copy(
                lines = state.lines.map { line ->
                    if (line.productId == productId) line.copy(qty = qty) else line
                }
            )
        }
    }

    fun save(note: String?) {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                saveStockCount(customerId, state.lines, note)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(StockCountEvent.Saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(StockCountEvent.Error(e.message ?: "Không lưu được phiếu kiểm kê"))
            }
        }
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
