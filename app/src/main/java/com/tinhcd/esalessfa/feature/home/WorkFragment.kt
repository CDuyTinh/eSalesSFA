package com.tinhcd.esalessfa.feature.home

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentWorkBinding
import com.tinhcd.esalessfa.feature.customer.CustomerListMode
import com.tinhcd.esalessfa.feature.customer.CustomerListViewModel
import com.tinhcd.esalessfa.feature.sync.SyncMode
import com.tinhcd.esalessfa.feature.sync.SyncViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
