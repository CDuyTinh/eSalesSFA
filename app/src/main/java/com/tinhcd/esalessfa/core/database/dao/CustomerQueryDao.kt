package com.tinhcd.esalessfa.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Khách hàng kèm tên kênh.
 *
 * Kênh nằm ở bảng riêng nhưng thẻ khách hàng nào cũng hiện, nên lấy luôn bằng
 * LEFT JOIN thay vì để tầng trên tra thêm một lượt cho mỗi dòng.
 */
data class CustomerRow(
    @Embedded val customer: CustomerEntity,
    val channelName: String?,
)

/** Khách hàng trong tuyến kèm thông tin lượt ghé hôm nay. */
data class RouteCustomerRow(
    @Embedded val customer: CustomerEntity,
    val channelName: String?,
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
        SELECT c.*, ch.name AS channelName
        FROM customers c
        LEFT JOIN channels ch ON ch.id = c.channelId
        WHERE c.isActive = 1
          AND (:query = '' OR c.nameSearch LIKE '%' || :query || '%'
               OR c.code LIKE '%' || :query || '%')
          AND (:channelId IS NULL OR c.channelId = :channelId)
        ORDER BY c.name
        """
    )
    fun pagingAll(query: String, channelId: String?): PagingSource<Int, CustomerRow>

    /**
     * Tuyến của một thứ trong tuần.
     *
     * LEFT JOIN sang visits để biết đã ghé chưa mà không cần truy vấn thứ hai —
     * danh sách 30-40 dòng nếu mỗi dòng tự hỏi trạng thái sẽ thành N+1 query.
     *
     * Nối theo ID của lượt ghé MỚI NHẤT chứ không nối theo customerId: một khách
     * có thể được ghé nhiều lần trong ngày (ghé xong, check-out, rồi quay lại),
     * nối theo customerId sẽ cho ra mỗi lượt ghé một dòng và khách hàng đó xuất
     * hiện trùng trong danh sách, trên bản đồ, lẫn trong số đếm khách của tuyến.
     *
     * Lấy lượt mới nhất là đúng nghiệp vụ: đang ghé lại lần hai thì thẻ phải báo
     * "đang ghé", không phải "đã ghé xong" của lần trước.
     *
     * Thứ tự: đang ghé → chưa ghé → đã ghé xong, trong mỗi nhóm giữ nguyên thứ
     * tự tuyến. Cửa hàng đang mở dở nằm trên cùng vì đó là việc phải làm nốt;
     * cửa hàng đã xong đẩy xuống đáy vì không còn phải động tới. Số "thứ tự
     * viếng thăm" in trên thẻ và trên bản đồ lấy từ d.sortOrder nên không đổi
     * theo cách sắp xếp này.
     */
    @Query(
        """
        SELECT c.*, ch.name AS channelName, d.sortOrder AS sortOrder,
               v.checkInAt AS checkInAt, v.checkOutAt AS checkOutAt
        FROM sales_route_details d
        INNER JOIN sales_routes r ON r.id = d.routeId
        INNER JOIN customers c    ON c.id = d.customerId
        LEFT  JOIN channels ch    ON ch.id = c.channelId
        LEFT  JOIN visits v       ON v.id = (
                  SELECT v2.id FROM visits v2
                  WHERE v2.customerId = c.id AND v2.visitDate = :today
                  ORDER BY v2.checkInAt DESC LIMIT 1
              )
        WHERE r.salespersonId = :salespersonId
          AND r.dayOfWeek = :dayOfWeek
          AND (:query = '' OR c.nameSearch LIKE '%' || :query || '%' OR c.code LIKE '%' || :query || '%')
        ORDER BY
          CASE
              WHEN v.checkOutAt IS NOT NULL THEN 2
              WHEN v.checkInAt  IS NOT NULL THEN 0
              ELSE 1
          END,
          d.sortOrder
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
