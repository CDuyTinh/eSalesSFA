package com.tinhcd.esalessfa.feature.order

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.ItemFreeLineBinding
import com.tinhcd.esalessfa.databinding.ItemOrderLineBinding
import com.tinhcd.esalessfa.databinding.ItemProductPickBinding
import com.tinhcd.esalessfa.domain.model.Product
import java.text.NumberFormat
import java.util.Locale

private val money: NumberFormat = NumberFormat.getInstance(Locale("vi", "VN"))
private val qtyFormat: NumberFormat = NumberFormat.getInstance(Locale("vi", "VN")).apply {
    maximumFractionDigits = 3
}

/**
 * Giỏ hàng gồm hai loại dòng: hàng bán và hàng tặng.
 *
 * Dùng một adapter với hai viewType thay vì hai RecyclerView lồng nhau — hàng
 * tặng phải nằm ngay dưới danh sách bán để nhân viên đọc liền mạch.
 */
class CartAdapter(
    private val onQtyClick: (CartLine) -> Unit,
    private val onRemove: (Int) -> Unit,
) : ListAdapter<CartRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is CartRow.Sold -> TYPE_SOLD
        is CartRow.Gift -> TYPE_GIFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SOLD) {
            SoldViewHolder(ItemOrderLineBinding.inflate(inflater, parent, false))
        } else {
            GiftViewHolder(ItemFreeLineBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CartRow.Sold -> (holder as SoldViewHolder).bind(row.line, onQtyClick, onRemove)
            is CartRow.Gift -> (holder as GiftViewHolder).bind(row.item)
        }
    }

    class SoldViewHolder(private val binding: ItemOrderLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: CartLine, onQtyClick: (CartLine) -> Unit, onRemove: (Int) -> Unit) {
            binding.productName.text = line.productName
            binding.qtyPrice.text = binding.root.context.getString(
                R.string.order_qty_price,
                qtyFormat.format(line.line.qty),
                line.line.uomCode,
                money.format(line.line.unitPrice),
            )

            binding.discount.text = if (line.discount > 0) {
                binding.root.context.getString(R.string.order_line_discount, money.format(line.discount))
            } else {
                ""
            }
            binding.lineTotal.text = money.format(line.netAmount)

            binding.root.setOnClickListener { onQtyClick(line) }
            binding.removeButton.setOnClickListener { onRemove(line.line.lineNo) }
        }
    }

    class GiftViewHolder(private val binding: ItemFreeLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FreeItemUi) {
            binding.freeName.text = item.productName
            binding.freeQty.text = "x${qtyFormat.format(item.qty)}"
        }
    }

    private companion object {
        const val TYPE_SOLD = 0
        const val TYPE_GIFT = 1

        val DIFF = object : DiffUtil.ItemCallback<CartRow>() {
            override fun areItemsTheSame(old: CartRow, new: CartRow) = when {
                old is CartRow.Sold && new is CartRow.Sold ->
                    old.line.line.lineNo == new.line.line.lineNo
                old is CartRow.Gift && new is CartRow.Gift ->
                    old.item.productName == new.item.productName &&
                        old.item.programCode == new.item.programCode
                else -> false
            }

            override fun areContentsTheSame(old: CartRow, new: CartRow) = old == new
        }
    }
}

/** Một dòng hiển thị trong giỏ. */
sealed interface CartRow {
    data class Sold(val line: CartLine) : CartRow
    data class Gift(val item: FreeItemUi) : CartRow
}

class ProductPickAdapter(
    private val onClick: (Product) -> Unit,
) : ListAdapter<Product, ProductPickAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemProductPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), onClick)

    class ViewHolder(private val binding: ItemProductPickBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product, onClick: (Product) -> Unit) {
            binding.name.text = product.name
            binding.code.text = product.code
            binding.root.setOnClickListener { onClick(product) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(old: Product, new: Product) = old.id == new.id
            override fun areContentsTheSame(old: Product, new: Product) = old == new
        }
    }
}
