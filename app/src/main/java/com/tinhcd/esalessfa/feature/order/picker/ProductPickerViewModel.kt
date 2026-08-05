package com.tinhcd.esalessfa.feature.order.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.product.Product
import com.tinhcd.esalessfa.domain.model.util.SearchText
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
