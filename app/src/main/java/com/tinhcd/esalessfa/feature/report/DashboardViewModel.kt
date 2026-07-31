package com.tinhcd.esalessfa.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.repository.DailyRevenue
import com.tinhcd.esalessfa.domain.repository.DashboardKpi
import com.tinhcd.esalessfa.domain.repository.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardUiState(
    val kpi: DashboardKpi = DashboardKpi(),
    val daily: List<DailyRevenue> = emptyList(),
    val topProducts: List<RankedItem> = emptyList(),
    val topCustomers: List<RankedItem> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: ReportRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeKpi(),
        repository.observeDailyRevenue(days = 14),
        repository.observeTopProducts(),
        repository.observeTopCustomers(),
    ) { kpi, daily, products, customers ->
        DashboardUiState(kpi, daily, products, customers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
