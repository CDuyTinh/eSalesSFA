package com.tinhcd.esalessfa.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.usecase.ObserveWorkSummaryUseCase
import com.tinhcd.esalessfa.domain.usecase.WorkSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WorkViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    visitRepository: VisitRepository,
    observeWorkSummary: ObserveWorkSummaryUseCase,
) : ViewModel() {

    /**
     * Các con số đã tải về, gom trong [ObserveWorkSummaryUseCase].
     *
     * Mỗi nguồn là Flow từ Room nên khi sync ghi dữ liệu mới, màn hình tự cập
     * nhật — không cần gọi refresh thủ công sau khi đồng bộ xong.
     */
    val uiState: StateFlow<WorkSummary> = observeWorkSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkSummary())

    /**
     * Tên cửa hàng đang chặn đồng bộ, null nếu không có.
     *
     * Hiện lý do thay vì để nút bấm không phản hồi — nhân viên sẽ tưởng app hỏng.
     */
    val blockingCustomer: StateFlow<String?> = visitRepository.observeActiveVisit()
        .map { it?.customerName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val routeCount: StateFlow<Int> = customerRepository
        .routeCustomers(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), query = "")
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
