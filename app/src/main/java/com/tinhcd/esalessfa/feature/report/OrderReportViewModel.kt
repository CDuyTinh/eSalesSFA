package com.tinhcd.esalessfa.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.repository.OrderSummary
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.usecase.ExportOrderReportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DateRange(val from: String, val to: String)

sealed interface OrderReportEvent {
    /** File đã ghi xong; Fragment lo phần dựng Uri và mở hộp thoại chia sẻ. */
    data class Exported(val filePath: String) : OrderReportEvent

    data object ExportFailed : OrderReportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OrderReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val exportOrderReport: ExportOrderReportUseCase,
) : ViewModel() {

    private val range = MutableStateFlow(
        DateRange(
            from = LocalDate.now().minusDays(29).toString(),
            to = LocalDate.now().toString(),
        )
    )
    val currentRange: StateFlow<DateRange> = range

    val orders: StateFlow<List<OrderSummary>> = range
        .flatMapLatest { repository.observeOrders(it.from, it.to) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<OrderReportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setRange(from: String, to: String) {
        range.value = DateRange(from, to)
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
}
