package com.tinhcd.esalessfa.feature.order.picker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tinhcd.esalessfa.databinding.ItemProductPickBinding
import com.tinhcd.esalessfa.domain.model.product.Product

class ProductPickAdapter(
    private val onClick: (Product) -> Unit,
) : PagingDataAdapter<Product, ProductPickAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemProductPickBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    /** getItem trả null ở vị trí chưa tải xong; enablePlaceholders = false nên hiếm khi gặp. */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onClick) }
    }

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
