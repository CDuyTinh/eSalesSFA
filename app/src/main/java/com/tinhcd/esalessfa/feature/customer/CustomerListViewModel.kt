package com.tinhcd.esalessfa.feature.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.util.SearchText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class CustomerListMode { ROUTE_TODAY, ALL }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    private val repository: CustomerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val mode: CustomerListMode = savedStateHandle.get<String>(ARG_MODE)
        ?.let { runCatching { CustomerListMode.valueOf(it) }.getOrNull() }
        ?: CustomerListMode.ROUTE_TODAY

    /**
     * Giữ trong SavedStateHandle để từ khoá tìm kiếm sống sót qua process death —
     * user quay lại từ màn chi tiết không phải gõ lại.
     */
    private val queryInput = MutableStateFlow(savedStateHandle[ARG_QUERY] ?: "")
    val currentQuery: StateFlow<String> = queryInput

    private val channelFilter = MutableStateFlow<String?>(null)

    /**
     * debounce 300ms: mỗi ký tự gõ vào mà chạy ngay một truy vấn thì với 200+ dòng
     * sẽ thấy giật. distinctUntilChanged chặn truy vấn lặp khi user gõ rồi xoá.
     */
    private val debouncedQuery: Flow<String> = queryInput
        .debounce(SEARCH_DEBOUNCE_MS)
        .map { SearchText.normalize(it) }
        .distinctUntilChanged()

    val pagedCustomers: Flow<PagingData<com.tinhcd.esalessfa.domain.model.Customer>> =
        combine(debouncedQuery, channelFilter) { query, channel -> query to channel }
            .flatMapLatest { (query, channel) -> repository.pagedCustomers(query, channel) }
            .cachedIn(viewModelScope)

    val routeCustomers: StateFlow<List<RouteCustomer>> = debouncedQuery
        .flatMapLatest { query ->
            repository.routeCustomers(Calendar.getInstance().get(Calendar.DAY_OF_WEEK), query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(value: String) {
        queryInput.value = value
    }

    fun onChannelSelected(channelId: String?) {
        channelFilter.value = channelId
    }

    companion object {
        const val ARG_MODE = "mode"
        private const val ARG_QUERY = "query"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
