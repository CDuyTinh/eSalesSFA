package com.tinhcd.esalessfa.feature.order.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.report.OrderDetail
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Chi tiết một đơn đã đặt — chỉ để xem lại, không sửa.
 *
 * Đọc thẳng từ Room qua Flow nên đơn vừa được đẩy lên server xong là nhãn đồng
 * bộ trên màn tự đổi, không cần mở lại màn.
 */
@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    repository: ReportRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: String = requireNotNull(savedStateHandle[ARG_ORDER_ID]) {
        "Thiếu $ARG_ORDER_ID — màn chi tiết đơn phải được mở kèm mã đơn"
    }

    val order: StateFlow<OrderDetail?> = repository.observeOrderDetail(orderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        const val ARG_ORDER_ID = "orderId"
    }
}
