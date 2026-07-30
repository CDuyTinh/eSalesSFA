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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.core.ui.isEmbedded
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentOrderReportBinding
import com.tinhcd.esalessfa.databinding.ItemOrderReportBinding
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject

data class DateRange(val from: String, val to: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderReportViewModel @Inject constructor(
    private val repository: ReportRepository,
) : ViewModel() {

    private val range = MutableStateFlow(
        DateRange(
            from = LocalDate.now().minusDays(29).toString(),
            to = LocalDate.now().toString(),
        )
    )
    val currentRange: StateFlow<DateRange> = range

    val orders: StateFlow<List<OrderSummary>> = range
        .flatMapLatest { repository.observeOrders(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(from: String, to: String) {
        range.value = DateRange(from, to)
    }

    /**
     * Xuất CSV.
     *
     * Thêm BOM UTF-8 ở đầu file: thiếu nó thì Excel trên Windows đọc tiếng Việt
     * thành ký tự rác. Đây là lỗi rất hay gặp khi xuất báo cáo cho khách VN.
     */
    fun buildCsv(orders: List<OrderSummary>): String = buildString {
        append('﻿')
        appendLine("Ma don;Ngay;Khach hang;So dong;Thanh tien;Trang thai;Da dong bo")
        orders.forEach { o ->
            // Thay ; trong tên khách hàng để không vỡ cột.
            val name = o.customerName.replace(';', ',')
            appendLine(
                "${o.orderNo};${o.orderDate};$name;${o.lineCount};" +
                    "${o.totalAmount};${o.status};${if (o.isSynced) "Roi" else "Chua"}"
            )
        }
    }
}

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

        binding.orderList.layoutManager = LinearLayoutManager(requireContext())
        binding.orderList.adapter = adapter

        // Làm tab thì không có gì để quay lại — navigateUp() sẽ pop cả màn Home.
        if (isEmbedded) binding.toolbar.navigationIcon = null
        else binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rangeButton.setOnClickListener { showRangePicker() }
        binding.exportButton.setOnClickListener { exportCsv(viewModel.orders.value) }

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

    private fun exportCsv(orders: List<OrderSummary>) {
        if (orders.isEmpty()) return

        runCatching {
            val dir = File(requireContext().cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, "bao-cao-don-hang-${LocalDate.now()}.csv")
            file.writeText(viewModel.buildCsv(orders))

            // FileProvider thay vì file:// — Android 7+ ném FileUriExposedException
            // khi chia sẻ đường dẫn file thô ra ngoài app.
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.report_share)))
        }.onFailure {
            Snackbar.make(requireView(), R.string.report_export_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}
