package com.tinhcd.esalessfa.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.report.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.usecase.DashboardSnapshot
import com.tinhcd.esalessfa.domain.usecase.ObserveDashboardUseCase
import com.tinhcd.esalessfa.domain.usecase.ObserveRevenueSeriesUseCase
import com.tinhcd.esalessfa.domain.usecase.RevenueRange
import com.tinhcd.esalessfa.domain.usecase.RevenueSeries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val topProducts: List<RankedItem> = emptyList(),
    val topCustomers: List<RankedItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: ReportRepository,
    observeDashboard: ObserveDashboardUseCase,
    private val observeRevenueSeries: ObserveRevenueSeriesUseCase,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        observeDashboard(),
        repository.observeTopProducts(),
        repository.observeTopCustomers(),
    ) { snapshot, products, customers ->
        DashboardUiState(snapshot, products, customers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val range = MutableStateFlow(RevenueRange.THIS_WEEK)

    /**
     * Biểu đồ theo khoảng đang chọn.
     *
     * Tách khỏi [uiState] để đổi tuần/tháng không kéo theo việc dựng lại cả màn
     * hình: các thẻ KPI phía trên không phụ thuộc khoảng thời gian này.
     */
    val revenueSeries: StateFlow<RevenueSeries> = range
        .flatMapLatest { observeRevenueSeries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RevenueSeries())

    fun onRangeSelected(value: RevenueRange) {
        range.value = value
    }
}
