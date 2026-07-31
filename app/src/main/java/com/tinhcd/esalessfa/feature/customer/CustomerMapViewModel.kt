package com.tinhcd.esalessfa.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.usecase.ObserveTodayRouteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class CustomerMapViewModel @Inject constructor(
    observeTodayRoute: ObserveTodayRouteUseCase,
) : ViewModel() {

    /**
     * Khách trong tuyến hôm nay CÓ toạ độ.
     *
     * Lọc ngay ở đây thay vì để màn hình bỏ qua lúc vẽ ghim: số điểm hiện trên
     * chú giải phải là số ghim thật sự thấy được, không phải số dòng trong danh
     * sách.
     */
    val customers: StateFlow<List<RouteCustomer>> =
        observeTodayRoute(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), query = "")
            .map { route -> route.customers.filter { it.customer.location != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
