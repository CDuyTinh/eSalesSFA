package com.tinhcd.esalessfa.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import kotlinx.coroutines.flow.Flow

/** Khách hàng trong tuyến kèm thông tin lượt ghé hôm nay. */
data class RouteCustomerRow(
    @Embedded val customer: CustomerEntity,
    val sortOrder: Int,
    val checkInAt: Long?,
    val checkOutAt: Long?,
)

@Dao
interface CustomerQueryDao {

    /**
     * Danh sách phân trang, lọc theo từ khoá và kênh.
     *
     * `:channelId IS NULL OR ...` cho phép dùng chung một câu truy vấn cho cả
     * trường hợp có lọc và không lọc, thay vì viết hai hàm gần giống nhau.
     */
    @Query(
        """
        SELECT * FROM customers
        WHERE isActive = 1
          AND (:query = '' OR nameSearch LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%')
          AND (:channelId IS NULL OR channelId = :channelId)
        ORDER BY name
        """
    )
    fun pagingAll(query: String, channelId: String?): PagingSource<Int, CustomerEntity>

    /**
     * Tuyến của một thứ trong tuần.
     *
     * LEFT JOIN sang visits để biết đã ghé chưa mà không cần truy vấn thứ hai —
     * danh sách 30-40 dòng nếu mỗi dòng tự hỏi trạng thái sẽ thành N+1 query.
     */
    @Query(
        """
        SELECT c.*, d.sortOrder AS sortOrder, v.checkInAt AS checkInAt, v.checkOutAt AS checkOutAt
        FROM sales_route_details d
        INNER JOIN sales_routes r ON r.id = d.routeId
        INNER JOIN customers c    ON c.id = d.customerId
        LEFT  JOIN visits v       ON v.customerId = c.id AND v.visitDate = :today
        WHERE r.salespersonId = :salespersonId
          AND r.dayOfWeek = :dayOfWeek
          AND (:query = '' OR c.nameSearch LIKE '%' || :query || '%' OR c.code LIKE '%' || :query || '%')
        ORDER BY d.sortOrder
        """
    )
    fun observeRoute(
        salespersonId: String,
        dayOfWeek: Int,
        today: String,
        query: String,
    ): Flow<List<RouteCustomerRow>>

    @Query("SELECT COUNT(*) FROM customers WHERE isActive = 1")
    fun observeCount(): Flow<Int>
}

@Dao
interface CatalogQueryDao {

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    fun observeProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM promotion_programs WHERE fromDate <= :today AND toDate >= :today")
    fun observeActivePromotionCount(today: String): Flow<Int>
}
