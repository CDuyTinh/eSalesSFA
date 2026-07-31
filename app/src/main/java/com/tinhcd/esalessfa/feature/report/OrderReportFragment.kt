package com.tinhcd.esalessfa.feature.report

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.feature.common.isEmbedded
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentOrderReportBinding
import com.tinhcd.esalessfa.databinding.ItemOrderReportBinding
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private class OrderReportAdapter :
    ListAdapter<OrderSummary, OrderReportAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemOrderReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemOrderReportBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val money = NumberFormat.getInstance(Locale("vi", "VN"))

        fun bind(item: OrderSummary) {
            binding.orderNo.text = item.orderNo
            binding.orderDate.text = item.orderDate
            binding.customerName.text = item.customerName
            binding.lineCount.text = binding.root.context
                .getString(R.string.report_line_count, item.lineCount)
            binding.totalAmount.text = money.format(item.totalAmount)

            binding.syncBadge.visibility = if (item.isSynced) View.GONE else View.VISIBLE
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<OrderSummary>() {
            override fun areItemsTheSame(old: OrderSummary, new: OrderSummary) = old.id == new.id
            override fun areContentsTheSame(old: OrderSummary, new: OrderSummary) = old == new
        }
    }
}

@AndroidEntryPoint
class OrderReportFragment : Fragment(R.layout.fragment_order_report) {

    private val viewModel: OrderReportViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderReportBinding.bind(view)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))
        val adapter = OrderReportAdapter()

        view.padTopForStatusBar()

        binding.orderList.layoutManager = LinearLayoutManager(requireContext())
        binding.orderList.adapter = adapter

        // Làm tab thì không có gì để quay lại — navigateUp() sẽ pop cả màn Home.
        if (isEmbedded) binding.toolbar.navigationIcon = null
        else binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rangeButton.setOnClickListener { showRangePicker() }
        binding.exportButton.setOnClickListener { viewModel.exportCsv() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.orders.collect { orders ->
                        adapter.submitList(orders)
                        binding.summaryText.text = getString(
                            R.string.report_summary,
                            orders.size,
                            money.format(orders.sumOf { it.totalAmount }),
                        )
                        binding.emptyText.visibility =
                            if (orders.isEmpty()) View.VISIBLE else View.GONE
                        binding.exportButton.isEnabled = orders.isNotEmpty()
                    }
                }
                launch {
                    viewModel.currentRange.collect { range ->
                        binding.rangeButton.text =
                            getString(R.string.report_range, range.from, range.to)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is OrderReportEvent.Exported -> shareCsv(event.filePath)

                            OrderReportEvent.ExportFailed -> Snackbar.make(
                                view,
                                R.string.report_export_failed,
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun showRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.report_pick_range)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            // MaterialDatePicker trả epoch millis theo UTC; đổi bằng múi giờ máy
            // sẽ lệch một ngày với người dùng ở UTC+7.
            val from = selection.first?.toLocalDate() ?: return@addOnPositiveButtonClickListener
            val to = selection.second?.toLocalDate() ?: from
            viewModel.setRange(from.toString(), to.toString())
        }
        picker.show(childFragmentManager, "range")
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    /**
     * Mở hộp thoại chia sẻ cho file ViewModel vừa ghi.
     *
     * FileProvider thay vì file:// — Android 7+ ném FileUriExposedException khi
     * chia sẻ đường dẫn file thô ra ngoài app.
     */
    private fun shareCsv(filePath: String) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            File(filePath),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.report_share)))
    }
}
