package com.tinhcd.esalessfa.feature.report

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.core.ui.isEmbedded
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.ui.BarEntry
import com.tinhcd.esalessfa.databinding.FragmentDashboardBinding
import com.tinhcd.esalessfa.domain.repository.DailyRevenue
import com.tinhcd.esalessfa.domain.repository.DashboardKpi
import com.tinhcd.esalessfa.domain.repository.RankedItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
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

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDashboardBinding.bind(view)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        // Làm tab thì không có gì để quay lại, và báo cáo đơn hàng đã là tab kế
        // bên nên không cần nút mở nữa.
        if (isEmbedded) {
            binding.toolbar.navigationIcon = null
            binding.reportButton.visibility = View.GONE
        } else {
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            binding.reportButton.setOnClickListener {
                findNavController().navigate(R.id.action_dashboard_to_orderReport)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.todayRevenue.text = money.format(state.kpi.todayRevenue)
                    binding.todayOrders.text = state.kpi.todayOrderCount.toString()
                    binding.todayVisits.text = state.kpi.todayVisitedCount.toString()

                    binding.monthRevenue.text = money.format(state.kpi.monthRevenue)
                    binding.monthOrders.text = state.kpi.monthOrderCount.toString()
                    binding.avgOrderValue.text = money.format(state.kpi.avgOrderValue)

                    // Nhãn chỉ lấy ngày trong tháng cho gọn; trục 14 cột mà ghi
                    // đủ yyyy-MM-dd thì chữ chồng lên nhau.
                    binding.chart.setEntries(
                        state.daily.map { BarEntry(it.date.takeLast(2), it.amount) }
                    )

                    binding.topProducts.text = state.topProducts.format(money)
                    binding.topCustomers.text = state.topCustomers.format(money)
                }
            }
        }
    }

    private fun List<RankedItem>.format(money: NumberFormat): String =
        if (isEmpty()) {
            getString(R.string.report_no_data)
        } else {
            mapIndexed { index, item ->
                "${index + 1}. ${item.name}  —  ${money.format(item.amount)}"
            }.joinToString("\n")
        }
}
