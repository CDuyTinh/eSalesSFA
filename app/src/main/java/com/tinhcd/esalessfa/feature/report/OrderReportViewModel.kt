package com.tinhcd.esalessfa.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.repository.CustomerReportItem
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.ProductReportItem
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.usecase.ExportOrderReportUseCase
import com.tinhcd.esalessfa.domain.util.SearchText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DateRange(val from: String, val to: String)

/**
 * Hai con số tóm tắt trên đầu mỗi tab.
 *
 * [count] mang nghĩa khác nhau theo tab — số đơn, số mặt hàng, số khách — nên
 * nhãn của nó do layout của từng tab đặt.
 */
data class ReportTotals(val amount: Long = 0, val count: Int = 0)

sealed interface OrderReportEvent {
    /** File đã ghi xong; Fragment lo phần dựng Uri và mở hộp thoại chia sẻ. */
    data class Exported(val filePath: String) : OrderReportEvent

    data object ExportFailed : OrderReportEvent
}

/**
 * ViewModel dùng chung cho màn báo cáo và cả ba tab con.
 *
 * Ba tab đọc cùng một khoảng ngày và cùng một bộ số liệu, nên chúng lấy chung
 * ViewModel này qua ownerProducer thay vì mỗi tab tự hỏi kho một lần — đổi kỳ ở
 * đầu màn là cả ba tab cùng đổi theo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val exportOrderReport: ExportOrderReportUseCase,
) : ViewModel() {

    private val range = MutableStateFlow(
        // Mặc định là tháng đang chạy, giống bộ lọc mở sẵn của bản eSales gốc.
        DateRange(
            from = LocalDate.now().withDayOfMonth(1).toString(),
            to = LocalDate.now().toString(),
        )
    )
    val currentRange: StateFlow<DateRange> = range

    val orders: StateFlow<List<OrderSummary>> = range
        .flatMapLatest { repository.observeOrders(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Đơn huỷ bị loại khỏi phần tổng.
     *
     * Danh sách bên dưới vẫn hiện chúng — nhân viên cần thấy đơn mình đã huỷ —
     * nhưng tính vào doanh số thì số của tab này sẽ vênh với tab Sản phẩm và
     * Khách hàng, hai chỗ đã loại đơn huỷ ngay trong truy vấn.
     */
    val orderTotals: StateFlow<ReportTotals> = orders
        .map { list ->
            val counted = list.filterNot { it.status == STATUS_CANCELLED }
            ReportTotals(amount = counted.sumOf { it.totalAmount }, count = counted.size)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportTotals())

    val products: StateFlow<List<ProductReportItem>> = range
        .flatMapLatest { repository.observeProductReport(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val productTotals: StateFlow<ReportTotals> = products
        .map { list -> ReportTotals(amount = list.sumOf { it.amount }, count = list.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportTotals())

    private val customerQuery = MutableStateFlow("")
    val currentQuery: StateFlow<String> = customerQuery

    private val allCustomers: StateFlow<List<CustomerReportItem>> = range
        .flatMapLatest { repository.observeCustomerReport(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Lọc ngay trên danh sách đã nạp, không chạy lại truy vấn.
     *
     * Báo cáo một kỳ chỉ vài chục tới vài trăm khách nên lọc trong bộ nhớ là đủ
     * nhanh, mà gõ tới đâu thấy tới đó — khỏi cần debounce như màn danh sách
     * khách hàng vốn phải phân trang cả nghìn dòng.
     */
    val customers: StateFlow<List<CustomerReportItem>> =
        combine(allCustomers, customerQuery) { list, query ->
            val keyword = SearchText.normalize(query)
            if (keyword.isEmpty()) list
            else list.filter {
                SearchText.normalize(it.name).contains(keyword) ||
                    SearchText.normalize(it.code).contains(keyword)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customerTotals: StateFlow<ReportTotals> = customers
        .map { list -> ReportTotals(amount = list.sumOf { it.amount }, count = list.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportTotals())

    private val _events = Channel<OrderReportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setRange(from: String, to: String) {
        range.value = DateRange(from, to)
    }

    fun onQueryChanged(value: String) {
        customerQuery.value = value
    }

    /**
     * Xuất CSV rồi báo đường dẫn cho màn hình.
     *
     * Định dạng và nơi ghi file nằm trong [ExportOrderReportUseCase]; Fragment
     * chỉ còn mỗi việc bật hộp thoại chia sẻ.
     */
    fun exportCsv() {
        val data = orders.value
        if (data.isEmpty()) return

        viewModelScope.launch {
            runCatching { exportOrderReport(data, LocalDate.now().toString()) }
                .onSuccess { path -> _events.send(OrderReportEvent.Exported(path)) }
                .onFailure { _events.send(OrderReportEvent.ExportFailed) }
        }
    }

    private companion object {
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
