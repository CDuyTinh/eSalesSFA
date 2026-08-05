package com.tinhcd.esalessfa.feature.order.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentOrderDetailBinding
import com.tinhcd.esalessfa.databinding.ItemOrderDetailLineBinding
import com.tinhcd.esalessfa.domain.model.report.OrderDetail
import com.tinhcd.esalessfa.domain.model.report.OrderDetailLine
import com.tinhcd.esalessfa.feature.common.asDateLabel
import com.tinhcd.esalessfa.feature.common.formatQty
import com.tinhcd.esalessfa.feature.common.moneyFormat
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Chi tiết một đơn đã đặt, mở từ tab "Đơn hàng" của màn báo cáo.
 *
 * Chỉ để xem: đơn đã gửi đi rồi thì sửa ở máy không còn ý nghĩa, muốn đổi phải
 * huỷ và đặt lại — đúng cách bản eSales gốc tách ReportOrderDetails khỏi màn
 * đặt hàng.
 */
@AndroidEntryPoint
class OrderDetailFragment : Fragment(R.layout.fragment_order_detail) {

    private val viewModel: OrderDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderDetailBinding.bind(view)

        view.padTopForStatusBar()
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.order.collect { order ->
                    if (order != null) bind(binding, order)
                }
            }
        }
    }

    private fun bind(binding: FragmentOrderDetailBinding, order: OrderDetail) {
        val money = moneyFormat()

        binding.orderNo.text = order.orderNo
        binding.customerName.text = order.customerName
        binding.customerAddress.text = getString(
            R.string.order_detail_customer,
            order.customerCode,
            order.customerAddress ?: getString(R.string.value_empty),
        )
        binding.orderDate.text = order.orderDate.asDateLabel()

        // Ngày giao là tuỳ chọn: đơn chưa hẹn giao thì giấu hẳn dòng đó đi thay
        // vì để một ô trống không nói lên điều gì.
        binding.deliveryRow.isVisible = order.deliveryDate != null
        binding.deliveryDate.text = order.deliveryDate?.asDateLabel().orEmpty()

        binding.note.isVisible = !order.note.isNullOrBlank()
        binding.note.text = order.note?.let { getString(R.string.order_detail_note, it) }

        binding.statusChip.setText(order.status.statusLabel())
        binding.statusChip.setBackgroundResource(order.status.statusChip())
        binding.statusChip.setTextColor(ContextCompat.getColor(requireContext(), order.status.statusColor()))
        binding.syncBadge.isVisible = !order.isSynced

        binding.subTotal.text = money.format(order.subTotal)
        binding.discount.text = getString(
            R.string.order_detail_discount,
            money.format(order.discountAmount),
        )
        binding.vat.text = money.format(order.vatAmount)
        binding.total.text = money.format(order.totalAmount)

        binding.linesTitle.text = getString(R.string.order_detail_lines, order.lines.size)
        binding.linesContainer.bindLines(order.lines)
    }

    private fun ViewGroup.bindLines(lines: List<OrderDetailLine>) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)
        val money = moneyFormat()

        lines.forEach { line ->
            val row = ItemOrderDetailLineBinding.inflate(inflater, this, false)

            row.productName.text = line.productName
            row.productCode.text = line.productCode
            row.qtyPrice.text = getString(
                R.string.order_qty_price,
                formatQty(line.qty),
                line.uomCode,
                money.format(line.price),
            )
            row.lineTotal.text = money.format(line.lineAmount)

            row.giftBadge.isVisible = line.isFreeItem
            row.discount.isVisible = line.discountAmount > 0
            row.discount.text = getString(
                R.string.order_line_discount,
                money.format(line.discountAmount),
            )

            addView(row.root)
        }
    }

    @StringRes
    private fun String.statusLabel(): Int = when (this) {
        STATUS_CONFIRMED -> R.string.order_status_confirmed
        STATUS_CANCELLED -> R.string.order_status_cancelled
        else -> R.string.order_status_new
    }

    @DrawableRes
    private fun String.statusChip(): Int = when (this) {
        STATUS_CONFIRMED -> R.drawable.bg_chip_green
        STATUS_CANCELLED -> R.drawable.bg_chip_red
        else -> R.drawable.bg_chip_blue
    }

    @ColorRes
    private fun String.statusColor(): Int = when (this) {
        STATUS_CONFIRMED -> R.color.stateGreen
        STATUS_CANCELLED -> R.color.brandRed
        else -> R.color.stateBlue
    }

    private companion object {
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
