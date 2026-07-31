package com.tinhcd.esalessfa.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.model.Salesperson
import com.tinhcd.esalessfa.domain.model.VisitState
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveTodayRouteUseCaseTest {

    private val customer = Customer(
        id = "KH01",
        code = "KH0001",
        name = "Cửa hàng Bình Minh",
        phone = "0900000001",
        address = "1 Đường số 1",
        location = GeoPoint(10.77, 106.70),
        channelId = "CH01",
        channelName = "Horeca",
        priceGroupId = "PG01",
        creditLimit = 0,
        debtAmount = 0,
        imageUrl = null,
    )

    @Test
    fun `ghep tuyen voi nguoi phu trach dang ma - ten`() = runTest {
        val useCase = ObserveTodayRouteUseCase(
            customerRepository = FakeCustomerRepository(
                listOf(RouteCustomer(customer, sortOrder = 3, visitState = VisitState.NOT_VISITED))
            ),
            salespersonRepository = FakeSalespersonRepository(
                Salesperson(
                    id = "NV01",
                    code = "NV001",
                    fullName = "Nguyễn Văn A",
                    branchId = null,
                    role = "SALES",
                )
            ),
        )

        val route = useCase(dayOfWeek = 6, query = "").first()

        assertThat(route.assignedTo).isEqualTo("NV001 - Nguyễn Văn A")
        assertThat(route.customers).hasSize(1)
        assertThat(route.customers.first().sortOrder).isEqualTo(3)
    }

    /** Chưa sync xong hồ sơ thì để trống, không in ra "null - null" trên mọi thẻ. */
    @Test
    fun `chua co ho so nhan vien thi de trong`() = runTest {
        val useCase = ObserveTodayRouteUseCase(
            customerRepository = FakeCustomerRepository(emptyList()),
            salespersonRepository = FakeSalespersonRepository(null),
        )

        val route = useCase(dayOfWeek = 6, query = "").first()

        assertThat(route.assignedTo).isEmpty()
        assertThat(route.customers).isEmpty()
    }

    @Test
    fun `hoi dung thu trong tuan va tu khoa tim kiem`() = runTest {
        val repository = FakeCustomerRepository(emptyList())
        val useCase = ObserveTodayRouteUseCase(repository, FakeSalespersonRepository(null))

        useCase(dayOfWeek = 4, query = "minh").first()

        assertThat(repository.requestedDayOfWeek).isEqualTo(4)
        assertThat(repository.requestedQuery).isEqualTo("minh")
    }
}

private class FakeCustomerRepository(
    private val route: List<RouteCustomer>,
) : CustomerRepository {

    var requestedDayOfWeek: Int? = null
        private set
    var requestedQuery: String? = null
        private set

    override fun routeCustomers(dayOfWeek: Int, query: String): Flow<List<RouteCustomer>> {
        requestedDayOfWeek = dayOfWeek
        requestedQuery = query
        return flowOf(route)
    }

    override fun pagedCustomers(query: String, channelId: String?): Flow<PagingData<Customer>> =
        flowOf(PagingData.empty())

    override suspend fun getById(id: String): Customer? = null

    override fun observeCustomerCount(): Flow<Int> = flowOf(0)
}

private class FakeSalespersonRepository(
    private val current: Salesperson?,
) : SalespersonRepository {

    override fun observeCurrent(): Flow<Salesperson?> = flowOf(current)
}
