package com.tinhcd.esalessfa.feature.order.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tinhcd.esalessfa.domain.model.product.Product
import com.tinhcd.esalessfa.domain.model.util.SearchText
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ProductPickerViewModel @Inject constructor(
    repository: ProductRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * debounce 300ms rồi mới chạy truy vấn: mỗi ký tự gõ vào mà dựng lại Pager
     * thì danh sách nhấp nháy và Room phải chạy lại từ trang đầu.
     *
     * cachedIn giữ dữ liệu đã tải qua các lần view bị dựng lại (xoay máy, quay
     * về từ popup nhập số lượng) nên không phải tải lại trang đang xem.
     */
    val products: Flow<PagingData<Product>> = query
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { SearchText.normalize(it) }
        .distinctUntilChanged()
        .flatMapLatest { repository.pagedProducts(it) }
        .cachedIn(viewModelScope)

    fun onQueryChanged(value: String) {
        query.value = value
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
