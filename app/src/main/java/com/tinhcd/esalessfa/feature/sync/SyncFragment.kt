package com.tinhcd.esalessfa.feature.sync

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentSyncBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SyncFragment : Fragment(R.layout.fragment_sync) {

    private val viewModel: SyncViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSyncBinding.bind(view)

        // KEEP policy nên gọi lại lúc xoay máy cũng không tạo thêm lượt nào.
        viewModel.start()

        binding.retryButton.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progress.visibility =
                        if (state.isRunning) View.VISIBLE else View.INVISIBLE

                    binding.statusText.text = when {
                        state.errorMessage != null -> getString(R.string.sync_failed)
                        state.isCompleted -> getString(R.string.sync_done)
                        state.isRunning -> getString(R.string.sync_running)
                        else -> getString(R.string.sync_preparing)
                    }

                    binding.detailText.text = when {
                        state.isCompleted ->
                            getString(R.string.sync_summary, state.totalRows, state.page)

                        state.page > 0 ->
                            getString(R.string.sync_progress, state.page, state.totalRows)

                        else -> ""
                    }

                    binding.errorText.text = state.errorMessage
                    binding.errorText.visibility =
                        if (state.errorMessage != null) View.VISIBLE else View.GONE
                    binding.retryButton.visibility =
                        if (state.errorMessage != null) View.VISIBLE else View.GONE

                    if (state.isCompleted) viewModel.onSyncCompleted()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finished.collect { finished ->
                    if (!finished) return@collect
                    // Lượt đầu sau đăng nhập thì đi tiếp vào Home; lượt do người
                    // dùng chủ động thì quay lại chỗ họ vừa đứng.
                    when (viewModel.mode) {
                        SyncMode.FIRST_RUN ->
                            findNavController().navigate(R.id.action_sync_to_home)

                        SyncMode.MANUAL -> findNavController().navigateUp()
                    }
                }
            }
        }
    }
}
