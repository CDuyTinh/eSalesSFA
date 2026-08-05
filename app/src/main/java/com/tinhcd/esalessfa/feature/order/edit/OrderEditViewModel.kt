package com.tinhcd.esalessfa.feature.order.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.order.OrderLine
import com.tinhcd.esalessfa.domain.model.order.OrderTotals
import com.tinhcd.esalessfa.domain.model.order.PromotionResult
import com.tinhcd.esalessfa.domain.model.promotion.PromotionProgram
import com.tinhcd.esalessfa.domain.usecase.BuildOrderLineResult
import com.tinhcd.esalessfa.domain.usecase.BuildOrderLineUseCase
import com.tinhcd.esalessfa.domain.usecase.CalculateOrderUseCase
import com.tinhcd.esalessfa.domain.usecase.ConfirmOrderUseCase
import com.tinhcd.esalessfa.domain.usecase.DescribeFreeItemsUseCase
import com.tinhcd.esalessfa.domain.usecase.LoadOrderContextUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Một dòng trong giỏ, kèm thông tin hiển thị. */
data class CartLine(
    val line: OrderLine,
    val productName: String,
    val productCode: String,
    val discount: Long = 0,
) {
    val netAmount: Long get() = (line.grossAmount - discount).coerceAtLeast(0)
}

data class OrderUiState(
    val customer: Customer? = null,
    val lines: List<CartLine> = emptyList(),
    val freeItems: List<FreeItemUi> = emptyList(),
    val appliedPromotions: List<String> = emptyList(),
    val totals: OrderTotals = OrderTotals(0, 0, 0, 0, 0),
    val manualDiscount: Long = 0,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val canConfirm: Boolean get() = lines.isNotEmpty() && !isSaving
}

data class FreeItemUi(val productName: String, val qty: Double, val programCode: String)

sealed interface OrderEvent {
    data class Saved(val orderNo: String) : OrderEvent
    data class Error(val message: String) : OrderEvent
}

@HiltViewModel
class OrderEditViewModel @Inject constructor(
    private val loadOrderContext: LoadOrderContextUseCase,
    private val describeFreeItems: DescribeFreeItemsUseCase,
    private val buildOrderLine: BuildOrderLineUseCase,
    private val calculateOrder: CalculateOrderUseCase,
    private val confirmOrder: ConfirmOrderUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[ARG_CUSTOMER_ID] ?: ""

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _events = Channel<OrderEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Nguồn sự thật của giỏ hàng. Mọi thay đổi đều đi qua đây rồi tính lại. */
    private val cart = mutableListOf<CartLine>()
    private var programs: List<PromotionProgram> = emptyList()
    private var manualDiscount: Long = 0

    init {
        viewModelScope.launch {
            val context = loadOrderContext(customerId)
            _uiState.update { it.copy(customer = context.customer) }
            programs = context.programs
            recalculate()
        }
    }

    fun addLine(productId: String, uomCode: String, qty: Double) {
        viewModelScope.launch {
            val customer = _uiState.value.customer ?: return@launch

            val built = buildOrderLine(
                lineNo = nextLineNo(),
                productId = productId,
                uomCode = uomCode,
                qty = qty,
                priceGroupId = customer.priceGroupId,
            )

            when (built) {
                BuildOrderLineResult.ProductNotFound -> return@launch

                BuildOrderLineResult.NoPrice -> {
                    _events.send(
                        OrderEvent.Error("Sản phẩm chưa có giá cho nhóm giá của khách hàng này")
                    )
                    return@launch
                }

                is BuildOrderLineResult.Built -> {
                    // Cùng sản phẩm + cùng đơn vị thì cộng dồn thay vì tạo dòng
                    // thứ hai — hai dòng trùng làm điều kiện khuyến mãi khó đọc
                    // và dễ nhập nhầm.
                    val existing = cart.indexOfFirst {
                        it.line.productId == productId && it.line.uomCode == uomCode
                    }

                    if (existing >= 0) {
                        val old = cart[existing]
                        cart[existing] = old.copy(line = old.line.copy(qty = old.line.qty + qty))
                    } else {
                        cart += CartLine(
                            line = built.line,
                            productName = built.productName,
                            productCode = built.productCode,
                        )
                    }
                }
            }
            recalculate()
        }
    }

    fun updateQty(lineNo: Int, qty: Double) {
        val index = cart.indexOfFirst { it.line.lineNo == lineNo }
        if (index < 0) return

        if (qty <= 0.0) {
            cart.removeAt(index)
        } else {
            val old = cart[index]
            cart[index] = old.copy(line = old.line.copy(qty = qty))
        }
        recalculate()
    }

    fun removeLine(lineNo: Int) {
        cart.removeAll { it.line.lineNo == lineNo }
        recalculate()
    }

    fun setManualDiscount(amount: Long) {
        manualDiscount = amount.coerceAtLeast(0)
        recalculate()
    }

    fun confirm(note: String?) {
        val state = _uiState.value
        if (!state.canConfirm) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                confirmOrder(
                    customerId = customerId,
                    lines = cart.map { it.line },
                    result = currentResult(),
                    note = note,
                )
            }.onSuccess { orderNo ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(OrderEvent.Saved(orderNo))
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
                _events.send(OrderEvent.Error(e.message ?: "Không lưu được đơn hàng"))
            }
        }
    }

    private fun currentResult(): PromotionResult =
        calculateOrder(cart.map { it.line }, programs, manualDiscount).promotion

    /**
     * Chạy lại TOÀN BỘ pipeline khuyến mãi sau mỗi thay đổi.
     *
     * Không tính tăng dần vì khuyến mãi phụ thuộc lẫn nhau: bỏ một dòng có thể
     * làm chương trình A mất điều kiện, và điều đó lại mở khoá cho chương trình B
     * vốn đang bị A loại trừ. Rule là hàm thuần và một lượt tính dưới 50ms cho
     * đơn 200 dòng, nên tính lại từ đầu vừa đúng vừa đủ nhanh.
     */
    private fun recalculate() {
        val lines = cart.map { it.line }
        val calculation = calculateOrder(lines, programs, manualDiscount)
        val result = calculation.promotion

        val decorated = cart.map { cartLine ->
            cartLine.copy(discount = result.discountForLine(cartLine.line.lineNo))
        }
        cart.clear()
        cart += decorated

        viewModelScope.launch {
            val freeUi = describeFreeItems(result.freeItems).map { free ->
                FreeItemUi(
                    productName = free.productName,
                    qty = free.qty,
                    programCode = free.programCode,
                )
            }

            _uiState.update {
                it.copy(
                    lines = decorated,
                    freeItems = freeUi,
                    appliedPromotions = result.appliedProgramCodes,
                    totals = calculation.totals,
                    manualDiscount = manualDiscount,
                )
            }
        }
    }

    private fun nextLineNo(): Int = (cart.maxOfOrNull { it.line.lineNo } ?: 0) + 1

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
