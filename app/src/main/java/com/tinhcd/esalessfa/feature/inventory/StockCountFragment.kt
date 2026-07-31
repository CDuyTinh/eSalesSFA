package com.tinhcd.esalessfa.feature.inventory

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentStockCountBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class StockCountFragment : Fragment(R.layout.fragment_stock_count) {

    private val viewModel: StockCountViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentStockCountBinding.bind(view)
        val qtyFormat = NumberFormat.getInstance(Locale("vi", "VN"))

        view.padTopForStatusBar()

        val adapter = StockCountAdapter(viewModel::onQtyChanged)

        binding.lineList.layoutManager = LinearLayoutManager(requireContext())
        binding.lineList.adapter = adapter
        // Tắt animation: đổi số trong ô sẽ kích hoạt animation của item và làm
        // ô nhập nhấp nháy trong lúc người dùng đang gõ.
        binding.lineList.itemAnimator = null

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.saveButton.setOnClickListener { viewModel.save(note = null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.toolbar.subtitle = state.customer?.name

                        adapter.submitList(state.lines)

                        binding.emptyText.visibility =
                            if (!state.isLoading && state.lines.isEmpty()) View.VISIBLE else View.GONE

                        binding.progressText.text = getString(
                            R.string.stock_progress,
                            state.countedCount,
                            state.totalCount,
                        )
                        binding.suggestSummary.text = if (state.suggestTotal > 0) {
                            getString(
                                R.string.stock_suggest_summary,
                                qtyFormat.format(state.suggestTotal),
                            )
                        } else {
                            getString(R.string.stock_no_suggestion)
                        }
                        binding.saveButton.isEnabled = state.canSave
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            StockCountEvent.Saved -> {
                                Snackbar.make(view, R.string.stock_saved, Snackbar.LENGTH_SHORT).show()
                                findNavController().navigateUp()
                            }

                            is StockCountEvent.Error ->
                                Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
