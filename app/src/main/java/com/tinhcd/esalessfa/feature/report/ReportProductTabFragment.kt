package com.tinhcd.esalessfa.feature.report

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.tinhcd.esalessfa.databinding.FragmentReportProductTabBinding
import com.tinhcd.esalessfa.databinding.ItemReportProductBinding
import com.tinhcd.esalessfa.domain.model.report.ProductReportItem
import com.tinhcd.esalessfa.feature.common.formatQty
import com.tinhcd.esalessfa.feature.common.moneyFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private class ReportProductAdapter :
    ListAdapter<ProductReportItem, ReportProductAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemReportProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemReportProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val money = moneyFormat()

        fun bind(item: ProductReportItem) {
            val context = binding.root.context
            binding.name.text = item.name
            binding.code.text = item.code
            binding.qty.text = context.getString(R.string.report_product_qty, formatQty(item.qty))
            binding.amount.text = money.format(item.amount)
            binding.orderCount.text =
                context.getString(R.string.report_product_orders, item.orderCount)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ProductReportItem>() {
            override fun areItemsTheSame(old: ProductReportItem, new: ProductReportItem) =
                old.id == new.id

            override fun areContentsTheSame(old: ProductReportItem, new: ProductReportItem) =
                old == new
        }
    }
}

/**
 * Tab "Sản phẩm": mặt hàng nào mang về nhiều tiền nhất trong kỳ.
 *
 * Danh sách đã được truy vấn xếp giảm dần theo doanh số, nên đọc từ trên xuống
 * là ra ngay nhóm hàng chủ lực — đúng cách bản gốc bày ở report_product.
 */
@AndroidEntryPoint
class ReportProductTabFragment : Fragment(R.layout.fragment_report_product_tab) {

    private val viewModel: OrderReportViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentReportProductTabBinding.bind(view)
        val money = moneyFormat()
        val adapter = ReportProductAdapter()

        binding.productList.layoutManager = LinearLayoutManager(requireContext())
        binding.productList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentRange.collect { range ->
                        binding.rangeTitle.text = range.label(requireContext())
                    }
                }
                launch {
                    viewModel.productTotals.collect { totals ->
                        binding.totalAmount.text = money.format(totals.amount)
                        binding.totalCount.text = totals.count.toString()
                    }
                }
                launch {
                    viewModel.products.collect { products ->
                        adapter.submitList(products)
                        binding.emptyText.visibility =
                            if (products.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
