package com.tinhcd.esalessfa.feature.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentReportCustomerTabBinding
import com.tinhcd.esalessfa.databinding.ItemReportCustomerBinding
import com.tinhcd.esalessfa.domain.repository.CustomerReportItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private class ReportCustomerAdapter :
    ListAdapter<CustomerReportItem, ReportCustomerAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReportCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemReportCustomerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val money = moneyFormat()

        fun bind(item: CustomerReportItem) {
            val context = binding.root.context
            binding.name.text = item.name
            binding.code.text = item.code
            binding.skuCount.text =
                context.getString(R.string.report_customer_sku, item.skuCount)
            binding.amount.text = money.format(item.amount)
            binding.orderCount.text =
                context.getString(R.string.report_product_orders, item.orderCount)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CustomerReportItem>() {
            override fun areItemsTheSame(old: CustomerReportItem, new: CustomerReportItem) =
                old.id == new.id

            override fun areContentsTheSame(old: CustomerReportItem, new: CustomerReportItem) =
                old == new
        }
    }
}

/**
 * Tab "Khách hàng": ai mua nhiều nhất trong kỳ, có ô tìm theo mã hoặc tên.
 *
 * Ô tổng đọc theo danh sách ĐÃ lọc: gõ tên một khách là thấy ngay doanh số
 * riêng của khách đó, giống bản gốc tính lại tổng sau mỗi lần lọc.
 */
@AndroidEntryPoint
class ReportCustomerTabFragment : Fragment(R.layout.fragment_report_customer_tab) {

    private val viewModel: OrderReportViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReportCustomerTabBinding.bind(view)
        val money = moneyFormat()
        val adapter = ReportCustomerAdapter()

        binding.customerList.layoutManager = LinearLayoutManager(requireContext())
        binding.customerList.adapter = adapter

        // Ô tìm kiếm chỉ set text một lần: gán lại mỗi khi state đổi sẽ đẩy con trỏ
        // về đầu dòng trong lúc user đang gõ.
        binding.searchInput.setText(viewModel.currentQuery.value)
        binding.searchInput.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentRange.collect { range ->
                        binding.rangeTitle.text = range.label(requireContext())
                    }
                }
                launch {
                    viewModel.customerTotals.collect { totals ->
                        binding.totalAmount.text = money.format(totals.amount)
                        binding.totalCount.text = totals.count.toString()
                    }
                }
                launch {
                    viewModel.customers.collect { customers ->
                        adapter.submitList(customers)
                        binding.emptyText.visibility =
                            if (customers.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
