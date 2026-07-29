package com.tinhcd.esalessfa.feature.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagingDataAdapter
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.ItemCustomerBinding
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.model.VisitState

/**
 * So sánh theo id cho areItemsTheSame và theo nội dung cho areContentsTheSame.
 * Data class nên equals() đã đúng, không phải tự viết.
 */
private val CUSTOMER_DIFF = object : DiffUtil.ItemCallback<Customer>() {
    override fun areItemsTheSame(old: Customer, new: Customer) = old.id == new.id
    override fun areContentsTheSame(old: Customer, new: Customer) = old == new
}

private val ROUTE_DIFF = object : DiffUtil.ItemCallback<RouteCustomer>() {
    override fun areItemsTheSame(old: RouteCustomer, new: RouteCustomer) =
        old.customer.id == new.customer.id

    override fun areContentsTheSame(old: RouteCustomer, new: RouteCustomer) = old == new
}

class CustomerViewHolder(
    private val binding: ItemCustomerBinding,
    private val onClick: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(customer: Customer, order: Int?, state: VisitState?) {
        binding.name.text = customer.name
        binding.code.text = customer.code
        binding.address.text = customer.address.orEmpty()

        binding.order.text = order?.toString().orEmpty()
        binding.order.visibility = if (order != null) View.VISIBLE else View.GONE

        binding.status.visibility = if (state != null) View.VISIBLE else View.GONE
        if (state != null) {
            binding.status.setText(
                when (state) {
                    VisitState.NOT_VISITED -> R.string.visit_not_yet
                    VisitState.IN_PROGRESS -> R.string.visit_in_progress
                    VisitState.DONE -> R.string.visit_done
                }
            )
            binding.status.setTextColor(
                binding.root.context.getColor(
                    when (state) {
                        VisitState.NOT_VISITED -> android.R.color.darker_gray
                        VisitState.IN_PROGRESS -> android.R.color.holo_orange_dark
                        VisitState.DONE -> android.R.color.holo_green_dark
                    }
                )
            )
        }

        binding.root.setOnClickListener { onClick(customer.id) }
    }
}

private fun inflate(parent: ViewGroup) = ItemCustomerBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
)

/** Danh sách toàn bộ khách hàng — dữ liệu lớn nên dùng Paging. */
class CustomerPagingAdapter(
    private val onClick: (String) -> Unit,
) : PagingDataAdapter<Customer, CustomerViewHolder>(CUSTOMER_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CustomerViewHolder(inflate(parent), onClick)

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, order = null, state = null) }
    }
}

/** Tuyến hôm nay — vài chục dòng, tải hết một lần là đủ. */
class RouteCustomerAdapter(
    private val onClick: (String) -> Unit,
) : ListAdapter<RouteCustomer, CustomerViewHolder>(ROUTE_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CustomerViewHolder(inflate(parent), onClick)

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item.customer, order = position + 1, state = item.visitState)
    }
}
