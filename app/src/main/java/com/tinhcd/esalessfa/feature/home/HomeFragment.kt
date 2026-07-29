package com.tinhcd.esalessfa.feature.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.database.dao.CustomerDao
import com.tinhcd.esalessfa.core.database.dao.ProductDao
import com.tinhcd.esalessfa.core.database.dao.PromotionDao
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.sync.SyncManager
import com.tinhcd.esalessfa.databinding.FragmentHomeBinding
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val customerDao: CustomerDao,
    private val productDao: ProductDao,
    private val promotionDao: PromotionDao,
    private val salespersonDao: SalespersonDao,
    private val syncRepository: SyncRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshCounts()

        viewModelScope.launch {
            syncRepository.observeLastSyncedAt().collect { at ->
                _uiState.update { it.copy(lastSyncedAt = at) }
            }
        }
        viewModelScope.launch {
            syncRepository.observePendingCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val salesperson = salespersonDao.getCurrent()
            val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

            _uiState.update {
                it.copy(
                    salespersonName = salesperson?.fullName.orEmpty(),
                    customerCount = customerDao.count(),
                    productCount = productDao.count(),
                    promotionCount = promotionDao.count(),
                )
            }

            // Tuyến hôm nay — bằng chứng rằng quan hệ route -> route_detail ->
            // customer đã về đủ, không chỉ riêng lẻ từng bảng.
            salesperson?.let { sp ->
                customerDao.observeRouteCustomers(sp.id, dayOfWeek).collect { list ->
                    _uiState.update { it.copy(routeCustomerCount = list.size) }
                }
            }
        }
    }

    fun syncAgain() = syncManager.startDownload(force = true)
}

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view)
        val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        binding.syncButton.setOnClickListener { viewModel.syncAgain() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.greetingText.text =
                        getString(R.string.home_greeting, state.salespersonName)
                    binding.customerCount.text = state.customerCount.toString()
                    binding.productCount.text = state.productCount.toString()
                    binding.promotionCount.text = state.promotionCount.toString()
                    binding.routeCount.text = state.routeCustomerCount.toString()
                    binding.pendingCount.text = state.pendingCount.toString()
                    binding.lastSyncText.text = state.lastSyncedAt?.let {
                        getString(R.string.home_last_sync, timeFormat.format(Date(it)))
                    } ?: getString(R.string.home_never_synced)
                }
            }
        }
    }
}
