package com.tinhcd.esalessfa.feature.order

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentOrderEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class OrderEditFragment : Fragment(R.layout.fragment_order_edit) {

    private val viewModel: OrderEditViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderEditBinding.bind(view)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        val adapter = CartAdapter(
            onQtyClick = { line -> showQtyDialog(line) },
            onRemove = { lineNo -> viewModel.removeLine(lineNo) },
        )
        binding.lineList.layoutManager = LinearLayoutManager(requireContext())
        binding.lineList.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.addProductButton.setOnClickListener { openProductPicker() }
        binding.confirmButton.setOnClickListener { viewModel.confirm(note = null) }

        // Màn chọn sản phẩm trả kết quả qua savedStateHandle của back stack entry —
        // cách chính thống của Navigation Component, không cần interface callback
        // hay EventBus.
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Bundle>(RESULT_PRODUCT)
            ?.observe(viewLifecycleOwner) { result ->
                viewModel.addLine(
                    productId = result.getString(KEY_PRODUCT_ID).orEmpty(),
                    uomCode = result.getString(KEY_UOM).orEmpty(),
                    qty = result.getDouble(KEY_QTY),
                )
                findNavController().currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<Bundle>(RESULT_PRODUCT)
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.toolbar.subtitle = state.customer?.name

                        val rows = state.lines.map { CartRow.Sold(it) } +
                            state.freeItems.map { CartRow.Gift(it) }
                        adapter.submitList(rows)

                        binding.emptyText.visibility =
                            if (rows.isEmpty()) View.VISIBLE else View.GONE

                        binding.subTotal.text = money.format(state.totals.subTotal)
                        binding.discountTotal.text =
                            if (state.totals.discountAmount > 0) {
                                "-${money.format(state.totals.discountAmount)}"
                            } else {
                                money.format(0)
                            }
                        binding.vatTotal.text = money.format(state.totals.vatAmount)
                        binding.grandTotal.text = money.format(state.totals.totalAmount)

                        binding.confirmButton.isEnabled = state.canConfirm

                        binding.promotionSummary.visibility =
                            if (state.appliedPromotions.isEmpty()) View.GONE else View.VISIBLE
                        binding.promotionSummary.text = getString(
                            R.string.order_applied_promotions,
                            state.appliedPromotions.joinToString(", "),
                        )
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is OrderEvent.Saved -> {
                                Snackbar.make(
                                    binding.root,
                                    getString(R.string.order_saved, event.orderNo),
                                    Snackbar.LENGTH_LONG,
                                ).show()
                                findNavController().navigateUp()
                            }

                            is OrderEvent.Error ->
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun openProductPicker() {
        // Ghi priceGroupId vào savedStateHandle của CHÍNH màn này: với màn chọn
        // sản phẩm, đây sẽ là previousBackStackEntry. Ghi ở màn trước đó nữa thì
        // picker không đọc được vì nó chỉ nhìn lùi đúng một bậc.
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                ProductPickerFragment.KEY_PRICE_GROUP,
                viewModel.uiState.value.customer?.priceGroupId.orEmpty(),
            )

        findNavController().navigate(R.id.action_orderEdit_to_productPicker)
    }

    private fun showQtyDialog(line: CartLine) {
        InputQtyDialog.newInstance(
            productId = line.line.productId,
            productName = line.productName,
            presetUom = line.line.uomCode,
            presetQty = line.line.qty,
            priceGroupId = viewModel.uiState.value.customer?.priceGroupId.orEmpty(),
        ) { _, _, qty ->
            viewModel.updateQty(line.line.lineNo, qty)
        }.show(childFragmentManager, "qty")
    }

    companion object {
        const val RESULT_PRODUCT = "result_product"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_UOM = "uomCode"
        const val KEY_QTY = "qty"
    }
}
