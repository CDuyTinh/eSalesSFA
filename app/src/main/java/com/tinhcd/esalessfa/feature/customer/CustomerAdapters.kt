package com.tinhcd.esalessfa.feature.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagingDataAdapter
import coil3.load
import coil3.request.crossfade
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

/** Những gì một thẻ cần, gom lại để tránh danh sách tham số dài dằng dặc. */
data class CustomerCardData(
    val customer: Customer,
    val visitOrder: Int? = null,
    val visitState: VisitState? = null,
    val assignedTo: String = "",
)

class CustomerViewHolder(
    private val binding: ItemCustomerBinding,
    private val onClick: (String) -> Unit,
    private val onCheckIn: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(data: CustomerCardData) {
        val customer = data.customer

        binding.name.text = customer.name
        binding.code.text = customer.code
        binding.initial.text = customer.name.trim().take(1).uppercase()

        // Ảnh nằm đè lên chữ cái đầu: có ảnh thì hiện, không thì để lộ chữ bên
        // dưới. Nhờ vậy lúc ảnh đang tải ô vẫn có nội dung.
        if (customer.imageUrl.isNullOrBlank()) {
            binding.photo.visibility = View.GONE
        } else {
            binding.photo.visibility = View.VISIBLE
            binding.photo.load(customer.imageUrl) { crossfade(true) }
        }

        binding.visitOrder.bindText(
            data.visitOrder?.let {
                binding.root.context.getString(R.string.customer_visit_order, it)
            }
        )
        binding.channel.bindRow(binding.channelRow, customer.channelName)
        binding.phone.bindRow(binding.phoneRow, customer.phone)
        binding.address.bindRow(binding.addressRow, customer.address)
        binding.assignee.bindRow(
            binding.assigneeRow,
            data.assignedTo.takeIf { it.isNotBlank() }?.let {
                binding.root.context.getString(R.string.customer_assignee, it)
            }
        )

        bindVisitState(data.visitState, customer.id)

        binding.root.setOnClickListener { onClick(customer.id) }
    }

    /**
     * Danh sách toàn bộ khách hàng không có khái niệm lượt ghé, nên giấu cả nhãn
     * trạng thái lẫn nút — thao tác ghé chỉ có nghĩa với khách trong tuyến.
     */
    private fun bindVisitState(state: VisitState?, customerId: String) {
        if (state == null) {
            binding.status.visibility = View.GONE
            binding.checkInButton.visibility = View.GONE
            return
        }

        binding.status.visibility = View.VISIBLE
        binding.status.setText(
            when (state) {
                VisitState.NOT_VISITED -> R.string.visit_not_yet
                VisitState.IN_PROGRESS -> R.string.visit_in_progress
                VisitState.DONE -> R.string.visit_done
            }
        )

        // Nền nhạt + chữ đậm cùng tông: đọc được trạng thái từ xa mà không cần
        // nhìn kỹ chữ, giống cách bản gốc phân biệt bằng màu.
        val (bg, fg) = when (state) {
            VisitState.NOT_VISITED -> R.drawable.bg_chip_orange to R.color.stateOrange
            VisitState.IN_PROGRESS -> R.drawable.bg_chip_blue to R.color.stateBlue
            VisitState.DONE -> R.drawable.bg_chip_green to R.color.stateGreen
        }
        binding.status.setBackgroundResource(bg)
        binding.status.setTextColor(binding.root.context.getColor(fg))

        // Ghé xong rồi thì không còn nút: mở lại màn viếng thăm cũng không làm
        // được gì. Đang ghé dở thì nút đổi thành Check-out.
        binding.checkInButton.visibility = if (state == VisitState.DONE) View.GONE else View.VISIBLE
        binding.checkInButton.setText(
            if (state == VisitState.IN_PROGRESS) R.string.action_check_out
            else R.string.action_check_in
        )
        binding.checkInButton.setOnClickListener { onCheckIn(customerId) }
    }
}

/** Ẩn cả dòng khi không có dữ liệu, thay vì để lại một biểu tượng trơ trọi. */
private fun android.widget.TextView.bindRow(row: View, value: String?) {
    row.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    text = value.orEmpty()
}

private fun android.widget.TextView.bindText(value: String?) {
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    text = value.orEmpty()
}

private fun inflate(parent: ViewGroup) = ItemCustomerBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
)

/** Danh sách toàn bộ khách hàng — dữ liệu lớn nên dùng Paging. */
class CustomerPagingAdapter(
    private val onClick: (String) -> Unit,
) : PagingDataAdapter<Customer, CustomerViewHolder>(CUSTOMER_DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CustomerViewHolder(inflate(parent), onClick, onCheckIn = {})

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(CustomerCardData(customer = it)) }
    }
}

/** Tuyến hôm nay — vài chục dòng, tải hết một lần là đủ. */
class RouteCustomerAdapter(
    private val onClick: (String) -> Unit,
    private val onCheckIn: (String) -> Unit,
) : ListAdapter<RouteCustomer, CustomerViewHolder>(ROUTE_DIFF) {

    /**
     * Người phụ trách tuyến, in trên mọi thẻ.
     *
     * Giữ ở adapter thay vì nhét vào từng RouteCustomer: nó giống nhau ở mọi
     * dòng và không thuộc về khách hàng nào cả.
     */
    var assignedTo: String = ""
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        CustomerViewHolder(inflate(parent), onClick, onCheckIn)

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            CustomerCardData(
                customer = item.customer,
                visitOrder = item.sortOrder,
                visitState = item.visitState,
                assignedTo = assignedTo,
            )
        )
    }
}
