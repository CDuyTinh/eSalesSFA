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
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.ui.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentWorkBinding
import com.tinhcd.esalessfa.domain.repository.CatalogRepository
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.feature.customer.CustomerListMode
import com.tinhcd.esalessfa.feature.customer.CustomerListViewModel
import com.tinhcd.esalessfa.feature.sync.SyncMode
import com.tinhcd.esalessfa.feature.sync.SyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class WorkUiState(
    val salespersonName: String = "",
    val customerCount: Int = 0,
    val productCount: Int = 0,
    val promotionCount: Int = 0,
    val lastSyncedAt: Long? = null,
    val pendingCount: Int = 0,
)

@HiltViewModel
class WorkViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    catalogRepository: CatalogRepository,
    salespersonRepository: SalespersonRepository,
    syncRepository: SyncRepository,
    visitRepository: VisitRepository,
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
    val uiState: StateFlow<WorkUiState> = combine(
        salespersonRepository.observeCurrent().map { it?.fullName.orEmpty() },
        counts,
        syncInfo,
    ) { name, (customers, products, promotions), (lastSync, pending) ->
        WorkUiState(
            salespersonName = name,
            customerCount = customers,
            productCount = products,
            promotionCount = promotions,
            lastSyncedAt = lastSync,
            pendingCount = pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkUiState())

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

/**
 * Tab "Công việc": lời chào, các con số đã tải về, tuyến hôm nay và đồng bộ.
 */
@AndroidEntryPoint
class WorkFragment : Fragment(R.layout.fragment_work) {

    private val viewModel: WorkViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentWorkBinding.bind(view)
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        // Tab tự chừa chỗ cho status bar, không phải màn Home: Home còn giữ thanh
        // tab ở đáy và phải để tab con vẽ nền lên tận đỉnh máy.
        view.padTopForStatusBar()

        binding.syncButton.setOnClickListener {
            val blocking = viewModel.blockingCustomer.value
            if (blocking != null) {
                Snackbar.make(
                    view,
                    getString(R.string.sync_blocked_by_visit, blocking),
                    Snackbar.LENGTH_LONG,
                ).show()
            } else {
                // Mở màn Sync thay vì tự enqueue: người dùng cần thấy tiến trình
                // và lỗi. Việc khởi động do SyncViewModel làm.
                findNavController().navigate(
                    R.id.action_home_to_sync,
                    bundleOf(SyncViewModel.ARG_MODE to SyncMode.MANUAL.name),
                )
            }
        }

        // Tuyến hôm nay đã là một tab riêng nên chuyển tab, không mở thêm màn hình
        // thứ hai hiển thị cùng danh sách.
        binding.routeButton.setOnClickListener {
            (parentFragment as? HomeFragment)?.selectTab(R.id.tab_checkin)
        }

        binding.allCustomersButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_home_to_customerList,
                bundleOf(CustomerListViewModel.ARG_MODE to CustomerListMode.ALL.name),
            )
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
                launch {
                    viewModel.blockingCustomer.collect { name ->
                        binding.syncWarning.visibility =
                            if (name == null) View.GONE else View.VISIBLE
                        if (name != null) {
                            binding.syncWarning.text =
                                getString(R.string.sync_blocked_by_visit, name)
                        }
                    }
                }
            }
        }
    }
}
