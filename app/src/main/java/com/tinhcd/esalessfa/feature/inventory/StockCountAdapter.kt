package com.tinhcd.esalessfa.feature.inventory

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.ItemStockCountBinding
import com.tinhcd.esalessfa.domain.model.stock.StockCountLine
import java.text.NumberFormat
import java.util.Locale

private val qtyFormat: NumberFormat = NumberFormat.getInstance(Locale("vi", "VN")).apply {
    maximumFractionDigits = 3
    isGroupingUsed = false
}

/**
 * Lưới nhập tồn kho — RecyclerView có ô nhập liệu trên từng dòng.
 *
 * Đây là bài toán khó kinh điển: ViewHolder bị tái sử dụng khi cuộn, nên nếu
 * gắn TextWatcher một cách ngây thơ thì việc `setText` lúc bind sẽ kích hoạt
 * watcher của dòng CŨ và ghi số vào sản phẩm khác.
 *
 * Cách xử lý ở đây:
 *   1. Mỗi ViewHolder giữ MỘT watcher duy nhất, tạo một lần trong constructor.
 *   2. Trước khi setText thì gỡ watcher ra, gắn lại sau — thay đổi do code
 *      không bị hiểu nhầm thành người dùng gõ.
 *   3. Watcher đọc sản phẩm hiện tại qua `boundProductId` chứ không bắt giữ
 *      biến từ lần bind trước.
 *   4. Giá trị thật nằm ở ViewModel, adapter chỉ hiển thị. Cuộn đi rồi quay
 *      lại vẫn thấy đúng số đã nhập.
 */
class StockCountAdapter(
    private val onQtyChanged: (productId: String, qty: Double?) -> Unit,
) : ListAdapter<StockCountLine, StockCountAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemStockCountBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        onQtyChanged,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class ViewHolder(
        private val binding: ItemStockCountBinding,
        onQtyChanged: (String, Double?) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundProductId: String? = null

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val productId = boundProductId ?: return
                val text = s?.toString().orEmpty()
                // Ô trống nghĩa là CHƯA ĐẾM, khác hẳn đếm ra 0 — phân biệt bằng
                // null để phiếu kiểm kê không ghi nhầm "hết hàng".
                onQtyChanged(productId, if (text.isBlank()) null else text.toDoubleOrNull())
            }
        }

        fun bind(line: StockCountLine) {
            boundProductId = line.productId

            binding.productName.text = line.productName
            binding.productCode.text = line.productCode
            binding.uom.text = line.uomCode
            binding.prevQty.text = binding.root.context.getString(
                R.string.stock_prev_qty,
                qtyFormat.format(line.prevQty),
            )

            val newText = line.qty?.let { qtyFormat.format(it) }.orEmpty()
            if (binding.qtyInput.text?.toString() != newText) {
                binding.qtyInput.removeTextChangedListener(watcher)
                binding.qtyInput.setText(newText)
                binding.qtyInput.addTextChangedListener(watcher)
            } else {
                // Đảm bảo watcher luôn được gắn đúng một lần sau khi bind.
                binding.qtyInput.removeTextChangedListener(watcher)
                binding.qtyInput.addTextChangedListener(watcher)
            }

            binding.suggestQty.text = if (line.suggestQty > 0) {
                binding.root.context.getString(
                    R.string.stock_suggest_qty,
                    qtyFormat.format(line.suggestQty),
                )
            } else {
                ""
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<StockCountLine>() {
            override fun areItemsTheSame(old: StockCountLine, new: StockCountLine) =
                old.productId == new.productId

            /**
             * Cố ý BỎ QUA thay đổi của qty.
             *
             * Số lượng do chính ô nhập sinh ra; nếu coi nó là thay đổi nội dung
             * thì DiffUtil sẽ rebind dòng đang gõ và con trỏ nhảy về đầu sau
             * mỗi ký tự. Chỉ so các trường hiển thị.
             */
            override fun areContentsTheSame(old: StockCountLine, new: StockCountLine) =
                old.productName == new.productName &&
                    old.prevQty == new.prevQty &&
                    old.uomCode == new.uomCode
        }
    }
}
