package com.tinhcd.esalessfa.feature.order

import android.app.Dialog
import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.DialogInputQtyBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Nhập số lượng theo đơn vị tính.
 *
 * Hiển thị thành tiền ngay để user phát hiện nhập nhầm đơn vị trước khi thêm vào
 * đơn. Giá và danh sách đơn vị lấy từ [InputQtyViewModel]; hộp thoại chỉ vẽ.
 */
@AndroidEntryPoint
class InputQtyDialog : DialogFragment() {

    private val viewModel: InputQtyViewModel by viewModels()

    private var onConfirm: ((productId: String, uom: String, qty: Double) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogInputQtyBinding.inflate(layoutInflater)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        val productId = requireArguments()
            .getString(InputQtyViewModel.ARG_PRODUCT_ID).orEmpty()
        val productName = requireArguments()
            .getString(InputQtyViewModel.ARG_PRODUCT_NAME).orEmpty()
        val presetQty = requireArguments()
            .getDouble(InputQtyViewModel.ARG_PRESET_QTY, 1.0)

        binding.productName.text = productName
        binding.qtyInput.setText(formatQty(presetQty))

        fun refreshPreview(unitPrice: Long) {
            val qty = binding.qtyInput.text?.toString()?.toDoubleOrNull() ?: 0.0
            binding.pricePreview.text = getString(
                R.string.order_price_preview,
                money.format(unitPrice),
                money.format((unitPrice * qty).toLong()),
            )
        }

        binding.qtyInput.doAfterTextChanged { refreshPreview(viewModel.uiState.value.unitPrice) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Chỉ dựng lại chip khi danh sách đổi: dựng mỗi lần giá đổi sẽ
                    // xoá luôn chip đang chọn ngay dưới ngón tay người dùng.
                    if (binding.uomGroup.childCount != state.uoms.size) {
                        binding.uomGroup.removeAllViews()
                        state.uoms.forEach { uom ->
                            binding.uomGroup.addView(
                                Chip(requireContext()).apply {
                                    text = uom.code
                                    isCheckable = true
                                    setOnClickListener { viewModel.onUomSelected(uom.code) }
                                }
                            )
                        }
                    }

                    val index = state.uoms.indexOfFirst { it.code == state.selectedUom }
                    if (index >= 0) (binding.uomGroup.getChildAt(index) as? Chip)?.isChecked = true

                    refreshPreview(state.unitPrice)
                }
            }
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(R.string.order_confirm) { _, _ ->
                val qty = binding.qtyInput.text?.toString()?.toDoubleOrNull() ?: 0.0
                val uom = viewModel.uiState.value.selectedUom
                if (qty > 0 && uom.isNotEmpty()) {
                    onConfirm?.invoke(productId, uom, qty)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    private fun formatQty(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {

        fun newInstance(
            productId: String,
            productName: String,
            presetUom: String? = null,
            presetQty: Double = 1.0,
            priceGroupId: String,
            onConfirm: (productId: String, uom: String, qty: Double) -> Unit,
        ) = InputQtyDialog().apply {
            arguments = Bundle().apply {
                putString(InputQtyViewModel.ARG_PRODUCT_ID, productId)
                putString(InputQtyViewModel.ARG_PRODUCT_NAME, productName)
                putString(InputQtyViewModel.ARG_PRESET_UOM, presetUom)
                putDouble(InputQtyViewModel.ARG_PRESET_QTY, presetQty)
                putString(InputQtyViewModel.ARG_PRICE_GROUP, priceGroupId)
            }
            this.onConfirm = onConfirm
        }
    }
}
