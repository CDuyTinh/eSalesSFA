package com.tinhcd.esalessfa.domain.repository

// PagingData nằm trong artifact paging-common — Kotlin thuần, không kéo theo
// Android SDK (phần phụ thuộc Android là paging-runtime). Đây là ngoại lệ duy
// nhất trong package domain, và nó vẫn compile/test được trên JVM.
import androidx.paging.PagingData
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.customer.RouteCustomer
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {

    /**
     * Toàn bộ khách hàng, phân trang.
     *
     * Dùng Paging 3 vì DMS thật có hàng nghìn khách; tải hết vào RAM rồi lọc sẽ
     * giật khi cuộn. [query] là chuỗi đã bỏ dấu để khớp với cột nameSearch.
     */
    fun pagedCustomers(query: String, channelId: String?): Flow<PagingData<Customer>>

    /** Khách trong tuyến của [dayOfWeek], kèm trạng thái ghé trong ngày. */
    fun routeCustomers(dayOfWeek: Int, query: String): Flow<List<RouteCustomer>>

    suspend fun getById(id: String): Customer?

    fun observeCustomerCount(): Flow<Int>
}
