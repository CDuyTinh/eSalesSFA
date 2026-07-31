package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Tuyến hôm nay kèm người phụ trách.
 *
 * [assignedTo] dạng "DDCG223193 - Hà Thị Tường Vy", để trống khi chưa sync xong
 * hồ sơ nhân viên.
 */
data class TodayRoute(
    val customers: List<RouteCustomer> = emptyList(),
    val assignedTo: String = "",
)

/**
 * Gộp danh sách tuyến với hồ sơ nhân viên đang đăng nhập.
 *
 * Hai nguồn khác nhau nhưng mọi thẻ khách hàng đều in dòng "viếng thăm bởi",
 * nên ghép ở đây thay vì để màn hình tự đi hỏi hai kho rồi tự nối chuỗi.
 */
class ObserveTodayRouteUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val salespersonRepository: SalespersonRepository,
) {

    operator fun invoke(dayOfWeek: Int, query: String): Flow<TodayRoute> = combine(
        customerRepository.routeCustomers(dayOfWeek, query),
        salespersonRepository.observeCurrent().map { person ->
            person?.let { "${it.code} - ${it.fullName}" }.orEmpty()
        },
    ) { customers, assignedTo ->
        TodayRoute(customers = customers, assignedTo = assignedTo)
    }
}
