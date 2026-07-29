package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DailyRevenueRow(val orderDate: String, val amount: Long, val orderCount: Int)

data class TopItemRow(val id: String, val name: String, val amount: Long, val qty: Double)

data class OrderReportRow(
    val id: String,
    val orderNo: String,
    val orderDate: String,
    val customerName: String,
    val totalAmount: Long,
    val status: String,
    val syncStatus: String,
    val lineCount: Int,
)

/**
 * Truy vấn tổng hợp cho dashboard và báo cáo.
 *
 * Tất cả đều chạy trên Room chứ không gọi server: nhân viên xem được số liệu
 * ngay giữa đồng không sóng, và mỗi lần mở màn hình không tốn một round-trip.
 *
 * Đơn CANCELLED bị loại khỏi mọi phép tính doanh số — tính vào sẽ thổi phồng
 * kết quả và làm số không khớp với báo cáo trên hệ thống DMS.
 */
@Dao
interface ReportDao {

    @Query(
        """
        SELECT COALESCE(SUM(totalAmount), 0) FROM orders
        WHERE orderDate = :date AND status <> 'CANCELLED'
        """
    )
    fun observeRevenueOn(date: String): Flow<Long>

    @Query("SELECT COUNT(*) FROM orders WHERE orderDate = :date AND status <> 'CANCELLED'")
    fun observeOrderCountOn(date: String): Flow<Int>

    /** [yearMonth] dạng "2026-07"; so bằng LIKE vì orderDate lưu chuỗi ISO. */
    @Query(
        """
        SELECT COALESCE(SUM(totalAmount), 0) FROM orders
        WHERE orderDate LIKE :yearMonth || '%' AND status <> 'CANCELLED'
        """
    )
    fun observeRevenueInMonth(yearMonth: String): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) FROM orders
        WHERE orderDate LIKE :yearMonth || '%' AND status <> 'CANCELLED'
        """
    )
    fun observeOrderCountInMonth(yearMonth: String): Flow<Int>

    /** Doanh số từng ngày để vẽ biểu đồ cột. */
    @Query(
        """
        SELECT orderDate AS orderDate,
               SUM(totalAmount) AS amount,
               COUNT(*) AS orderCount
        FROM orders
        WHERE orderDate >= :fromDate AND status <> 'CANCELLED'
        GROUP BY orderDate
        ORDER BY orderDate
        """
    )
    fun observeDailyRevenue(fromDate: String): Flow<List<DailyRevenueRow>>

    /**
     * Top sản phẩm theo doanh thu.
     *
     * Loại hàng tặng vì chúng có netAmount = 0 nhưng số lượng lớn, sẽ làm lệch
     * cả bảng xếp hạng nếu tính chung.
     */
    @Query(
        """
        SELECT p.id AS id, p.name AS name,
               SUM(od.netAmount) AS amount, SUM(od.baseQty) AS qty
        FROM order_details od
        INNER JOIN orders o   ON o.id = od.orderId
        INNER JOIN products p ON p.id = od.productId
        WHERE o.orderDate >= :fromDate AND o.status <> 'CANCELLED' AND od.isFreeItem = 0
        GROUP BY p.id
        ORDER BY amount DESC
        LIMIT :limit
        """
    )
    fun observeTopProducts(fromDate: String, limit: Int = 5): Flow<List<TopItemRow>>

    @Query(
        """
        SELECT c.id AS id, c.name AS name,
               SUM(o.totalAmount) AS amount, COUNT(*) AS qty
        FROM orders o
        INNER JOIN customers c ON c.id = o.customerId
        WHERE o.orderDate >= :fromDate AND o.status <> 'CANCELLED'
        GROUP BY c.id
        ORDER BY amount DESC
        LIMIT :limit
        """
    )
    fun observeTopCustomers(fromDate: String, limit: Int = 5): Flow<List<TopItemRow>>

    @Query("SELECT COUNT(*) FROM visits WHERE visitDate = :date AND checkOutAt IS NOT NULL")
    fun observeVisitedCountOn(date: String): Flow<Int>

    /**
     * Danh sách đơn cho màn báo cáo.
     *
     * lineCount đếm bằng truy vấn con thay vì JOIN + GROUP BY để không phải gom
     * nhóm toàn bộ order_details khi chỉ cần vài chục đơn của khoảng ngày đang xem.
     */
    @Query(
        """
        SELECT o.id AS id, o.orderNo AS orderNo, o.orderDate AS orderDate,
               c.name AS customerName, o.totalAmount AS totalAmount,
               o.status AS status, o.syncStatus AS syncStatus,
               (SELECT COUNT(*) FROM order_details d WHERE d.orderId = o.id) AS lineCount
        FROM orders o
        INNER JOIN customers c ON c.id = o.customerId
        WHERE o.orderDate BETWEEN :fromDate AND :toDate
          AND (:customerId IS NULL OR o.customerId = :customerId)
        ORDER BY o.orderDate DESC, o.orderNo DESC
        """
    )
    fun observeOrderReport(
        fromDate: String,
        toDate: String,
        customerId: String?,
    ): Flow<List<OrderReportRow>>
}
