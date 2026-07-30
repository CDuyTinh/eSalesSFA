package com.tinhcd.esalessfa.feature.order

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.ui.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentProductPickerBinding
import com.tinhcd.esalessfa.domain.model.Product
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import com.tinhcd.esalessfa.domain.util.SearchText
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ProductPickerViewModel @Inject constructor(
    repository: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val products: StateFlow<List<Product>> = query
        .debounce(300)
        .map { SearchText.normalize(it) }
        .distinctUntilChanged()
        .flatMapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(value: String) {
        query.value = value
    }

}

@AndroidEntryPoint
class ProductPickerFragment : Fragment(R.layout.fragment_product_picker) {

    private val viewModel: ProductPickerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentProductPickerBinding.bind(view)

        // priceGroupId của khách hàng nằm ở màn giỏ hàng; lấy qua back stack
        // entry trước đó thay vì truyền vòng qua argument.
        val priceGroupId = findNavController().previousBackStackEntry
            ?.savedStateHandle
            ?.get<String>(KEY_PRICE_GROUP)
            .orEmpty()

        val adapter = ProductPickAdapter { product ->
            InputQtyDialog.newInstance(
                productId = product.id,
                productName = product.name,
                priceGroupId = priceGroupId,
            ) { productId, uom, qty ->
                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(
                        OrderEditFragment.RESULT_PRODUCT,
                        Bundle().apply {
                            putString(OrderEditFragment.KEY_PRODUCT_ID, productId)
                            putString(OrderEditFragment.KEY_UOM, uom)
                            putDouble(OrderEditFragment.KEY_QTY, qty)
                        },
                    )
                findNavController().navigateUp()
            }.show(childFragmentManager, "qty")
        }

        view.padTopForStatusBar()

        binding.productList.layoutManager = LinearLayoutManager(requireContext())
        binding.productList.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.searchInput.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.products.collect { adapter.submitList(it) }
            }
        }
    }

    companion object {
        const val KEY_PRICE_GROUP = "priceGroupId"
    }
}
