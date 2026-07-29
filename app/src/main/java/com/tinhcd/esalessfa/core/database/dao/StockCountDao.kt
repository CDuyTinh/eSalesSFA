package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountEntity
import kotlinx.coroutines.flow.Flow

/** Một dòng cần kiểm kê, kèm tồn kỳ trước để so sánh. */
data class StockCountRow(
    val productId: String,
    val productCode: String,
    val productName: String,
    val baseUom: String,
    val prevBaseQty: Double,
)

data class StockCountWithDetails(
    @Embedded val header: StockCountEntity,
    @Relation(parentColumn = "id", entityColumn = "stockCountId")
    val details: List<StockCountDetailEntity>,
)

@Dao
interface StockCountDao {

    /**
     * Danh sách SKU cần kiểm kê tại một cửa hàng.
     *
     * Lấy từ LỊCH SỬ MUA của chính khách hàng đó thay vì toàn bộ 300 sản phẩm:
     * nhân viên chỉ cần đếm những mặt hàng cửa hàng thực sự bán. Sắp theo số lần
     * mua giảm dần để mặt hàng chủ lực nằm trên đầu.
     *
     * prevBaseQty lấy từ lần kiểm kê gần nhất, 0 nếu chưa từng kiểm kê.
     */
    @Query(
        """
        SELECT p.id AS productId, p.code AS productCode, p.name AS productName,
               p.baseUom AS baseUom,
               COALESCE((
                   SELECT d.baseQty FROM stock_count_details d
                   INNER JOIN stock_counts s ON s.id = d.stockCountId
                   WHERE s.customerId = :customerId AND d.productId = p.id
                   ORDER BY s.countDate DESC LIMIT 1
               ), 0) AS prevBaseQty
        FROM products p
        WHERE p.isActive = 1
          AND p.id IN (
              SELECT DISTINCT od.productId FROM order_details od
              INNER JOIN orders o ON o.id = od.orderId
              WHERE o.customerId = :customerId AND od.isFreeItem = 0
          )
        ORDER BY (
            SELECT COUNT(*) FROM order_details od2
            INNER JOIN orders o2 ON o2.id = od2.orderId
            WHERE o2.customerId = :customerId AND od2.productId = p.id
        ) DESC, p.name
        """
    )
    suspend fun productsToCount(customerId: String): List<StockCountRow>

    @Transaction
    suspend fun save(header: StockCountEntity, details: List<StockCountDetailEntity>) {
        deleteDetails(header.id)
        upsertHeader(header)
        upsertDetails(details)
    }

    @Upsert suspend fun upsertHeader(header: StockCountEntity)
    @Upsert suspend fun upsertDetails(items: List<StockCountDetailEntity>)

    @Query("DELETE FROM stock_count_details WHERE stockCountId = :id")
    suspend fun deleteDetails(id: String)

    @Query("SELECT * FROM stock_counts WHERE customerId = :customerId AND countDate = :date LIMIT 1")
    suspend fun findToday(customerId: String, date: String): StockCountEntity?

    // ── outbox ──
    @Transaction
    @Query(
        """
        SELECT * FROM stock_counts
        WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < :maxAttempts
        ORDER BY clientCreatedAt LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int = 50, maxAttempts: Int = 5): List<StockCountWithDetails>

    @Query("UPDATE stock_counts SET syncStatus = :status, sessionId = :sessionId WHERE id IN (:ids)")
    suspend fun markStatus(ids: List<String>, status: SyncStatus, sessionId: String?)

    @Query("UPDATE stock_counts SET syncStatus = 'SYNCED', serverAckAt = :ackAt, lastError = NULL WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, ackAt: Long)

    @Query("UPDATE stock_counts SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("SELECT COUNT(*) FROM stock_counts WHERE syncStatus IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>
}
