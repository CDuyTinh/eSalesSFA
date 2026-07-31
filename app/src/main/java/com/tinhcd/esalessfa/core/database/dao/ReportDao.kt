package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DailyRevenueRow(val orderDate: String, val amount: Long, val orderCount: Int)

/** Số khách phải ghé của một thứ trong tuần, theo tuyến đã phân. */
data class RouteDayPlanRow(val dayOfWeek: Int, val customerCount: Int)

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

    @Query(
        """
        SELECT COUNT(*) FROM visits
        WHERE visitDate LIKE :yearMonth || '%' AND checkOutAt IS NOT NULL
        """
    )
    fun observeVisitedCountInMonth(yearMonth: String): Flow<Int>

    /**
     * Số lượt ghé CÓ phát sinh đơn — tử số của chỉ số PC (Productive Call).
     *
     * Đếm lượt ghé chứ không đếm đơn: ghé một lần mà viết hai đơn vẫn chỉ là một
     * lượt ghé hiệu quả, tính theo đơn sẽ đẩy PC vượt 100%.
     */
    @Query(
        """
        SELECT COUNT(*) FROM visits v
        WHERE v.visitDate LIKE :yearMonth || '%' AND v.checkOutAt IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM orders o
              WHERE o.customerId = v.customerId AND o.orderDate = v.visitDate
                AND o.status <> 'CANCELLED'
          )
        """
    )
    fun observeProductiveVisitCountInMonth(yearMonth: String): Flow<Int>

    /**
     * Số cặp (đơn, SKU) — chia cho số đơn ra chỉ số SKU/Đơn hàng.
     *
     * Đếm theo cặp chứ không đếm dòng: một sản phẩm đặt hai đơn vị (Thùng và Lẻ)
     * nằm trên hai dòng nhưng vẫn chỉ là MỘT mặt hàng trong giỏ. Hàng tặng bị
     * loại vì nhân viên không chủ động bán chúng.
     */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT od.orderId, od.productId
            FROM order_details od
            INNER JOIN orders o ON o.id = od.orderId
            WHERE o.orderDate = :date AND o.status <> 'CANCELLED' AND od.isFreeItem = 0
            GROUP BY od.orderId, od.productId
        )
        """
    )
    fun observeSkuLinesOn(date: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT od.orderId, od.productId
            FROM order_details od
            INNER JOIN orders o ON o.id = od.orderId
            WHERE o.orderDate LIKE :yearMonth || '%' AND o.status <> 'CANCELLED'
              AND od.isFreeItem = 0
            GROUP BY od.orderId, od.productId
        )
        """
    )
    fun observeSkuLinesInMonth(yearMonth: String): Flow<Int>

    /** Số khách hàng KHÁC NHAU đã mua trong tháng — tử số của độ phủ. */
    @Query(
        """
        SELECT COUNT(DISTINCT customerId) FROM orders
        WHERE orderDate LIKE :yearMonth || '%' AND status <> 'CANCELLED'
        """
    )
    fun observeBuyingCustomerCountInMonth(yearMonth: String): Flow<Int>

    /** Số khách phải ghé hôm nay: chỉ tiêu của đơn hàng và viếng thăm trong ngày. */
    @Query(
        """
        SELECT COUNT(DISTINCT d.customerId)
        FROM sales_route_details d
        INNER JOIN sales_routes r ON r.id = d.routeId
        WHERE r.dayOfWeek = :dayOfWeek
        """
    )
    fun observeRoutePlanOn(dayOfWeek: Int): Flow<Int>

    /**
     * Kế hoạch ghé theo từng thứ trong tuần.
     *
     * Nhân với số lần thứ đó đã trôi qua trong tháng sẽ ra chỉ tiêu ghé của cả
     * tháng — phép nhân đó làm ở tầng domain vì nó phụ thuộc "hôm nay là ngày
     * mấy", thứ mà SQL không nên tự quyết.
     */
    @Query(
        """
        SELECT r.dayOfWeek AS dayOfWeek, COUNT(DISTINCT d.customerId) AS customerCount
        FROM sales_route_details d
        INNER JOIN sales_routes r ON r.id = d.routeId
        GROUP BY r.dayOfWeek
        """
    )
    fun observeRoutePlanByWeekday(): Flow<List<RouteDayPlanRow>>

    /** Tổng số khách được phân tuyến — mẫu số của độ phủ khách hàng. */
    @Query("SELECT COUNT(DISTINCT customerId) FROM sales_route_details")
    fun observeRouteCustomerCount(): Flow<Int>

    /** Doanh số từng ngày trong một khoảng — dùng cho biểu đồ tuần/tháng. */
    @Query(
        """
        SELECT orderDate AS orderDate,
               SUM(totalAmount) AS amount,
               COUNT(*) AS orderCount
        FROM orders
        WHERE orderDate BETWEEN :fromDate AND :toDate AND status <> 'CANCELLED'
        GROUP BY orderDate
        ORDER BY orderDate
        """
    )
    fun observeRevenueBetween(fromDate: String, toDate: String): Flow<List<DailyRevenueRow>>

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
