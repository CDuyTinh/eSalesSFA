package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderPromotionEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.VisitEntity
import kotlinx.coroutines.flow.Flow

/** Lượt ghé đang mở, kèm tên cửa hàng để hiển thị lý do bị chặn. */
data class ActiveVisitRow(
    val visitId: String,
    val customerId: String,
    val customerName: String,
    val checkInAt: Long,
)

/** Đơn hàng kèm chi tiết và khuyến mãi — đọc cả cụm trong một lần truy vấn. */
data class OrderWithDetails(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val details: List<OrderDetailEntity>,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val promotions: List<OrderPromotionEntity>,
)

@Dao
interface VisitDao {

    @Upsert
    suspend fun upsert(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun getById(id: String): VisitEntity?

    /** Lượt ghé đang mở của khách hàng — dùng để biết đã check-in chưa. */
    @Query(
        """
        SELECT * FROM visits
        WHERE customerId = :customerId AND visitDate = :date AND checkOutAt IS NULL
        LIMIT 1
        """
    )
    suspend fun getOpenVisit(customerId: String, date: String): VisitEntity?

    @Query("SELECT * FROM visits WHERE visitDate = :date ORDER BY checkInAt")
    fun observeByDate(date: String): Flow<List<VisitEntity>>

    /**
     * Lượt ghé đang mở của một khách hàng, dạng Flow.
     *
     * Màn chi tiết khách hàng phải biết ngay khi quay lại từ check-in, nên phải
     * quan sát thay vì đọc một lần lúc khởi tạo.
     */
    @Query(
        """
        SELECT * FROM visits
        WHERE customerId = :customerId AND visitDate = :date AND checkOutAt IS NULL
        LIMIT 1
        """
    )
    fun observeOpenVisit(customerId: String, date: String): Flow<VisitEntity?>

    /**
     * Còn lượt ghé nào chưa check-out không.
     *
     * Dùng để chặn đồng bộ lên: đơn hàng, kiểm kê và khảo sát của một lượt viếng
     * thăm chưa kết thúc vẫn có thể bị sửa, đẩy lên sớm là gửi số liệu chưa chốt.
     */
    @Query("SELECT COUNT(*) FROM visits WHERE checkOutAt IS NULL")
    suspend fun countOpenVisits(): Int

    /**
     * Lượt ghé đang mở duy nhất trên toàn app, kèm tên cửa hàng.
     *
     * Trả về một bản ghi vì quy tắc nghiệp vụ chỉ cho phép ghé một cửa hàng tại
     * một thời điểm; nếu có nhiều dòng thì dữ liệu đã sai và lấy dòng sớm nhất
     * là lựa chọn an toàn.
     */
    @Query(
        """
        SELECT v.id AS visitId, v.customerId AS customerId,
               c.name AS customerName, v.checkInAt AS checkInAt
        FROM visits v
        INNER JOIN customers c ON c.id = v.customerId
        WHERE v.checkOutAt IS NULL
        ORDER BY v.checkInAt LIMIT 1
        """
    )
    fun observeActiveVisit(): Flow<ActiveVisitRow?>

    /** Bản đọc một lần, dùng ngay trước khi ghi để kiểm tra điều kiện. */
    @Query(
        """
        SELECT v.id AS visitId, v.customerId AS customerId,
               c.name AS customerName, v.checkInAt AS checkInAt
        FROM visits v
        INNER JOIN customers c ON c.id = v.customerId
        WHERE v.checkOutAt IS NULL
        ORDER BY v.checkInAt LIMIT 1
        """
    )
    suspend fun getActiveVisit(): ActiveVisitRow?

    // ── outbox ──
    // Outbox là một QUERY chứ không phải bảng riêng. Ghi trạng thái ngay trên bản
    // ghi nghiệp vụ giúp tránh dual-write (ghi hai nơi rồi lệch nhau).
    /**
     * Chỉ lấy lượt ghé đã upload xong ảnh check-in, giống cách bảng surveys chờ
     * ảnh minh chứng.
     *
     * sync-upload dùng `ignoreDuplicates`, nên một lượt ghé lên server rồi thì
     * gửi lại sẽ bị bỏ qua. Đẩy sớm lúc ảnh còn dở đồng nghĩa cột ảnh trên server
     * rỗng vĩnh viễn — đúng thứ dùng để đối soát nhân viên có tới cửa hàng.
     *
     * Điều kiện đọc là: hoặc lượt ghé không có ảnh, hoặc ảnh đã lên xong (lúc đó
     * checkInPhotoPath bị xoá và checkInPhotoUrl có giá trị).
     */
    @Query(
        """
        SELECT * FROM visits
        WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < :maxAttempts
          AND (checkInPhotoPath IS NULL OR checkInPhotoUrl IS NOT NULL)
        ORDER BY clientCreatedAt LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int = 50, maxAttempts: Int = 5): List<VisitEntity>

    @Query("UPDATE visits SET syncStatus = :status, sessionId = :sessionId WHERE id IN (:ids)")
    suspend fun markStatus(ids: List<String>, status: SyncStatus, sessionId: String?)

    @Query("UPDATE visits SET syncStatus = 'SYNCED', serverAckAt = :ackAt, lastError = NULL WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, ackAt: Long)

    @Query("UPDATE visits SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("SELECT COUNT(*) FROM visits WHERE syncStatus IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>
    /**
     * Ghi lại đường dẫn ảnh trên Storage sau khi đẩy lên xong.
     *
     * Xoá luôn đường dẫn trong máy: file đã bị xoá ngay sau khi upload nên giữ
     * lại chỉ khiến màn hình cố mở một file không còn tồn tại.
     *
     * Xoá checkInPhotoPath cũng chính là tín hiệu cho [getPending] biết lượt ghé
     * này đã đủ điều kiện đẩy lên server.
     */
    @Query("UPDATE visits SET checkInPhotoUrl = :url, checkInPhotoPath = NULL WHERE id = :visitId")
    suspend fun markPhotoUploaded(visitId: String, url: String)
}

@Dao
interface OrderDao {

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getWithDetails(id: String): OrderWithDetails?

    @Transaction
    @Query("SELECT * FROM orders WHERE orderDate = :date ORDER BY clientCreatedAt DESC")
    fun observeByDate(date: String): Flow<List<OrderWithDetails>>

    @Query("SELECT * FROM orders WHERE customerId = :customerId ORDER BY orderDate DESC LIMIT :limit")
    fun observeHistory(customerId: String, limit: Int = 20): Flow<List<OrderEntity>>

    /**
     * Ghi cả cụm đơn hàng trong MỘT transaction.
     *
     * Nếu mất điện giữa chừng, hoặc lưu được toàn bộ, hoặc không gì cả — không có
     * chuyện đơn tồn tại mà thiếu vài dòng chi tiết.
     */
    @Transaction
    suspend fun saveOrder(
        order: OrderEntity,
        details: List<OrderDetailEntity>,
        promotions: List<OrderPromotionEntity>,
    ) {
        deleteDetails(order.id)
        deletePromotions(order.id)
        upsertOrder(order)
        upsertDetails(details)
        upsertPromotions(promotions)
    }

    @Upsert suspend fun upsertOrder(order: OrderEntity)
    @Upsert suspend fun upsertDetails(items: List<OrderDetailEntity>)
    @Upsert suspend fun upsertPromotions(items: List<OrderPromotionEntity>)

    @Query("DELETE FROM order_details WHERE orderId = :orderId")
    suspend fun deleteDetails(orderId: String)

    @Query("DELETE FROM order_promotions WHERE orderId = :orderId")
    suspend fun deletePromotions(orderId: String)

    // ── outbox ──
    @Transaction
    @Query(
        """
        SELECT * FROM orders
        WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < :maxAttempts
        ORDER BY clientCreatedAt LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int = 50, maxAttempts: Int = 5): List<OrderWithDetails>

    @Query("UPDATE orders SET syncStatus = :status, sessionId = :sessionId WHERE id IN (:ids)")
    suspend fun markStatus(ids: List<String>, status: SyncStatus, sessionId: String?)

    @Query("UPDATE orders SET syncStatus = 'SYNCED', serverAckAt = :ackAt, lastError = NULL WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, ackAt: Long)

    @Query("UPDATE orders SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("SELECT COUNT(*) FROM orders WHERE syncStatus IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>

    /** Số thứ tự đơn trong ngày, để sinh orderNo. */
    @Query("SELECT COUNT(*) FROM orders WHERE salespersonId = :salespersonId AND orderDate = :date")
    suspend fun countByDate(salespersonId: String, date: String): Int
}
