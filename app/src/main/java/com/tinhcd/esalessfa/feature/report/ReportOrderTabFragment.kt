package com.tinhcd.esalessfa.feature.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
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
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentReportOrderTabBinding
import com.tinhcd.esalessfa.databinding.ItemOrderReportBinding
import com.tinhcd.esalessfa.domain.model.report.OrderSummary
import com.tinhcd.esalessfa.feature.common.asDateLabel
import com.tinhcd.esalessfa.feature.common.moneyFormat
import com.tinhcd.esalessfa.feature.order.detail.OrderDetailViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private class OrderReportAdapter(private val onClick: (OrderSummary) -> Unit) :
    ListAdapter<OrderSummary, OrderReportAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemOrderReportBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onClick,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemOrderReportBinding,
        private val onClick: (OrderSummary) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val money = moneyFormat()

        fun bind(item: OrderSummary) {
            binding.orderNo.text = item.orderNo
            binding.orderDate.text = item.orderDate.asDateLabel()
            binding.customerName.text = item.customerName
            binding.lineCount.text = binding.root.context
                .getString(R.string.report_line_count, item.lineCount)
            binding.totalAmount.text = money.format(item.totalAmount)

            binding.syncBadge.visibility = if (item.isSynced) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<OrderSummary>() {
            override fun areItemsTheSame(old: OrderSummary, new: OrderSummary) = old.id == new.id
            override fun areContentsTheSame(old: OrderSummary, new: OrderSummary) = old == new
        }
    }
}

/**
 * Tab "Đơn hàng": tổng doanh số và số đơn của kỳ, rồi danh sách đơn.
 *
 * Đơn đã huỷ vẫn nằm trong danh sách nhưng không được tính vào ô tổng — xem
 * [OrderReportViewModel.orderTotals].
 */
@AndroidEntryPoint
class ReportOrderTabFragment : Fragment(R.layout.fragment_report_order_tab) {

    private val viewModel: OrderReportViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReportOrderTabBinding.bind(view)
        val money = moneyFormat()

        // Điều hướng theo ID ĐÍCH thay vì ID action: màn báo cáo vừa là tab của
        // Home vừa là một đích riêng, mà action chỉ hợp lệ khi đang đứng đúng
        // đích khai nó.
        val adapter = OrderReportAdapter { order ->
            findNavController().navigate(
                R.id.orderDetailFragment,
                bundleOf(OrderDetailViewModel.ARG_ORDER_ID to order.id),
            )
        }

        binding.orderList.layoutManager = LinearLayoutManager(requireContext())
        binding.orderList.adapter = adapter
        binding.exportButton.setOnClickListener { viewModel.exportCsv() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentRange.collect { range ->
                        binding.rangeTitle.text = range.label(requireContext())
                    }
                }
                launch {
                    viewModel.orderTotals.collect { totals ->
                        binding.totalAmount.text = money.format(totals.amount)
                        binding.totalCount.text = totals.count.toString()
                    }
                }
                launch {
                    viewModel.orders.collect { orders ->
                        adapter.submitList(orders)
                        binding.emptyText.visibility =
                            if (orders.isEmpty()) View.VISIBLE else View.GONE

                        val pending = orders.count { !it.isSynced }
                        binding.pendingText.text =
                            if (pending == 0) getString(R.string.report_all_synced)
                            else getString(R.string.report_pending_count, pending)

                        binding.exportButton.isEnabled = orders.isNotEmpty()
                    }
                }
            }
        }
    }
}
