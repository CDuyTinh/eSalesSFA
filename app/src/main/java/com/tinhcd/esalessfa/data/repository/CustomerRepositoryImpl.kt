package com.tinhcd.esalessfa.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.tinhcd.esalessfa.core.database.dao.CatalogQueryDao
import com.tinhcd.esalessfa.core.database.dao.CustomerDao
import com.tinhcd.esalessfa.core.database.dao.CustomerQueryDao
import com.tinhcd.esalessfa.core.database.dao.RouteCustomerRow
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.data.mapper.toDomain
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.customer.RouteCustomer
import com.tinhcd.esalessfa.domain.model.customer.Salesperson
import com.tinhcd.esalessfa.domain.model.customer.VisitState
import com.tinhcd.esalessfa.domain.repository.CatalogRepository
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import com.tinhcd.esalessfa.domain.repository.SessionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class CustomerRepositoryImpl @Inject constructor(
    private val queryDao: CustomerQueryDao,
    private val customerDao: CustomerDao,
    private val salespersonDao: SalespersonDao,
) : CustomerRepository {

    override fun pagedCustomers(query: String, channelId: String?): Flow<PagingData<Customer>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                // Giữ sẵn vài trang quanh vị trí đang xem để cuộn không thấy khoảng trống.
                prefetchDistance = PAGE_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { queryDao.pagingAll(query, channelId) },
        ).flow.map { paging -> paging.map { it.customer.toDomain(it.channelName) } }

    override fun routeCustomers(dayOfWeek: Int, query: String): Flow<List<RouteCustomer>> =
        flow {
            val salesperson = salespersonDao.getCurrent()
            if (salesperson == null) {
                emit(emptyList())
                return@flow
            }
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            emitAll(
                queryDao.observeRoute(salesperson.id, dayOfWeek, today, query)
                    .map { rows -> rows.map { it.toRouteCustomer() } }
            )
        }

    override suspend fun getById(id: String): Customer? =
        customerDao.getById(id)?.let { it.customer.toDomain(it.channelName) }

    override fun observeCustomerCount(): Flow<Int> = queryDao.observeCount()

    private fun RouteCustomerRow.toRouteCustomer() = RouteCustomer(
        customer = customer.toDomain(channelName),
        sortOrder = sortOrder,
        visitState = when {
            checkOutAt != null -> VisitState.DONE
            checkInAt != null -> VisitState.IN_PROGRESS
            else -> VisitState.NOT_VISITED
        },
    )

    private companion object {
        const val PAGE_SIZE = 30
    }
}

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    private val dao: CatalogQueryDao,
) : CatalogRepository {

    override fun observeProductCount(): Flow<Int> = dao.observeProductCount()

    override fun observeActivePromotionCount(): Flow<Int> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return dao.observeActivePromotionCount(today)
    }
}

@Singleton
class SalespersonRepositoryImpl @Inject constructor(
    private val dao: SalespersonDao,
    private val sessionStore: SessionStore,
) : SalespersonRepository {

    override fun observeCurrent(): Flow<Salesperson?> =
        sessionStore.userId.flatMapLatest { userId ->
            if (userId == null) flowOf(null)
            else dao.observeByUserId(userId).map { entity ->
                entity?.let {
                    Salesperson(it.id, it.code, it.fullName, it.branchId, it.role)
                }
            }
        }
}
