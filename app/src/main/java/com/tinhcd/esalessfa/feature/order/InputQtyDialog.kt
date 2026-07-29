package com.tinhcd.esalessfa.feature.order

import android.app.Dialog
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.DialogInputQtyBinding
import com.tinhcd.esalessfa.domain.model.ProductUom
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Nhập số lượng theo đơn vị tính.
 *
 * Giá đổi theo đơn vị (Thùng khác Lẻ) nên phải tra lại mỗi khi user đổi chip,
 * và hiển thị thành tiền ngay để họ phát hiện nhập nhầm đơn vị trước khi thêm
 * vào đơn.
 */
@AndroidEntryPoint
class InputQtyDialog : DialogFragment() {

    @Inject lateinit var productRepository: ProductRepository

    private var onConfirm: ((productId: String, uom: String, qty: Double) -> Unit)? = null

    private var uoms: List<ProductUom> = emptyList()
    private var selectedUom: String = ""
    private var unitPrice: Long = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogInputQtyBinding.inflate(layoutInflater)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        val productId = requireArguments().getString(ARG_PRODUCT_ID).orEmpty()
        val productName = requireArguments().getString(ARG_PRODUCT_NAME).orEmpty()
        val presetUom = requireArguments().getString(ARG_PRESET_UOM)
        val presetQty = requireArguments().getDouble(ARG_PRESET_QTY, 1.0)
        val priceGroupId = requireArguments().getString(ARG_PRICE_GROUP).orEmpty()

        binding.productName.text = productName
        binding.qtyInput.setText(formatQty(presetQty))

        fun refreshPreview() {
            val qty = binding.qtyInput.text?.toString()?.toDoubleOrNull() ?: 0.0
            binding.pricePreview.text = getString(
                R.string.order_price_preview,
                money.format(unitPrice),
                money.format((unitPrice * qty).toLong()),
            )
        }

        fun selectUom(code: String) {
            selectedUom = code
            lifecycleScope.launch {
                unitPrice = productRepository.getPrice(priceGroupId, productId, code) ?: 0L
                refreshPreview()
            }
        }

        lifecycleScope.launch {
            uoms = productRepository.getById(productId)?.uoms.orEmpty()
            binding.uomGroup.removeAllViews()

            uoms.forEach { uom ->
                val chip = Chip(requireContext()).apply {
                    text = uom.code
                    isCheckable = true
                    setOnClickListener { selectUom(uom.code) }
                }
                binding.uomGroup.addView(chip)
            }

            val initial = presetUom
                ?: uoms.firstOrNull { it.isDefaultSale }?.code
                ?: uoms.firstOrNull()?.code
                ?: ""

            val index = uoms.indexOfFirst { it.code == initial }
            if (index >= 0) (binding.uomGroup.getChildAt(index) as? Chip)?.isChecked = true
            if (initial.isNotEmpty()) selectUom(initial)
        }

        binding.qtyInput.doAfterTextChanged { refreshPreview() }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.order_confirm) { _, _ ->
                val qty = binding.qtyInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                if (qty > 0 && selectedUom.isNotEmpty()) {
                    onConfirm?.invoke(productId, selectedUom, qty)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {
        private const val ARG_PRODUCT_ID = "productId"
        private const val ARG_PRODUCT_NAME = "productName"
        private const val ARG_PRESET_UOM = "presetUom"
        private const val ARG_PRESET_QTY = "presetQty"
        private const val ARG_PRICE_GROUP = "priceGroupId"

        fun newInstance(
            productId: String,
            productName: String,
            presetUom: String? = null,
            presetQty: Double = 1.0,
            priceGroupId: String,
            onConfirm: (productId: String, uom: String, qty: Double) -> Unit,
        ) = InputQtyDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_PRODUCT_ID, productId)
                putString(ARG_PRODUCT_NAME, productName)
                putString(ARG_PRESET_UOM, presetUom)
                putDouble(ARG_PRESET_QTY, presetQty)
                putString(ARG_PRICE_GROUP, priceGroupId)
            }
            this.onConfirm = onConfirm
        }
    }
}
