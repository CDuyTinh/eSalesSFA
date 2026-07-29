package com.tinhcd.esalessfa.feature.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.sync.SyncManager
import com.tinhcd.esalessfa.databinding.FragmentHomeBinding
import com.tinhcd.esalessfa.domain.repository.CatalogRepository
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import com.tinhcd.esalessfa.feature.customer.CustomerListMode
import com.tinhcd.esalessfa.feature.customer.CustomerListViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val salespersonName: String = "",
    val customerCount: Int = 0,
    val productCount: Int = 0,
    val promotionCount: Int = 0,
    val routeCustomerCount: Int = 0,
    val lastSyncedAt: Long? = null,
    val pendingCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    catalogRepository: CatalogRepository,
    salespersonRepository: SalespersonRepository,
    syncRepository: SyncRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    // combine chỉ có overload giữ nguyên kiểu tới 5 luồng; nhiều hơn sẽ rơi vào
    // bản vararg trả về Array<Any?> và mất hết type safety. Gộp theo hai nhóm.
    private val counts = combine(
        customerRepository.observeCustomerCount(),
        catalogRepository.observeProductCount(),
        catalogRepository.observeActivePromotionCount(),
    ) { customers, products, promotions -> Triple(customers, products, promotions) }

    private val syncInfo = combine(
        syncRepository.observeLastSyncedAt(),
        syncRepository.observePendingCount(),
    ) { lastSync, pending -> lastSync to pending }

    /**
     * Mỗi nguồn là Flow từ Room nên khi sync ghi dữ liệu mới, màn hình tự cập
     * nhật — không cần gọi refresh thủ công sau khi đồng bộ xong.
     */
    val uiState: StateFlow<HomeUiState> = combine(
        salespersonRepository.observeCurrent().map { it?.fullName.orEmpty() },
        counts,
        syncInfo,
    ) { name, (customers, products, promotions), (lastSync, pending) ->
        HomeUiState(
            salespersonName = name,
            customerCount = customers,
            productCount = products,
            promotionCount = promotions,
            lastSyncedAt = lastSync,
            pendingCount = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val routeCount: StateFlow<Int> = customerRepository
        .routeCustomers(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), query = "")
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Gửi outbox lên trước rồi mới tải xuống — xem SyncManager.startFullSync. */
    fun syncAgain() = syncManager.startFullSync()
}

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view)
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        binding.syncButton.setOnClickListener { viewModel.syncAgain() }
        binding.routeButton.setOnClickListener { openCustomerList(CustomerListMode.ROUTE_TODAY) }
        binding.allCustomersButton.setOnClickListener { openCustomerList(CustomerListMode.ALL) }
        binding.dashboardButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dashboard)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.greetingText.text =
                            getString(R.string.home_greeting, state.salespersonName)
                        binding.customerCount.text = state.customerCount.toString()
                        binding.productCount.text = state.productCount.toString()
                        binding.promotionCount.text = state.promotionCount.toString()
                        binding.pendingCount.text = state.pendingCount.toString()
                        binding.lastSyncText.text = state.lastSyncedAt?.let {
                            getString(R.string.home_last_sync, timeFormat.format(Date(it)))
                        } ?: getString(R.string.home_never_synced)
                    }
                }
                launch {
                    viewModel.routeCount.collect { binding.routeCount.text = it.toString() }
                }
            }
        }
    }

    private fun openCustomerList(mode: CustomerListMode) {
        findNavController().navigate(
            R.id.action_home_to_customerList,
            bundleOf(CustomerListViewModel.ARG_MODE to mode.name),
        )
    }
}
