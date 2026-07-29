package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.tinhcd.esalessfa.core.database.entity.local.PendingUploadEntity
import com.tinhcd.esalessfa.core.database.entity.local.SyncErrorEntity
import com.tinhcd.esalessfa.core.database.entity.local.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncStateDao {

    /** Mốc version của mọi bảng, gửi lên trong request sync-download. */
    @Query("SELECT * FROM sync_state")
    suspend fun getAll(): List<SyncStateEntity>

    @Query("SELECT lastVersion FROM sync_state WHERE tableName = :table")
    suspend fun getVersion(table: String): Long?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<SyncStateEntity>)

    @Query("SELECT MIN(lastSyncedAt) FROM sync_state")
    fun observeOldestSync(): Flow<Long?>

    /** Xoá sạch mốc version -> lần sync sau tải lại toàn bộ. Dùng khi đổi user. */
    @Query("DELETE FROM sync_state")
    suspend fun clear()
}

@Dao
interface PendingUploadDao {

    @Query("SELECT * FROM pending_uploads WHERE status IN ('PENDING','FAILED') ORDER BY createdAt LIMIT :limit")
    suspend fun getPending(limit: Int = 20): List<PendingUploadEntity>

    @Upsert
    suspend fun upsert(item: PendingUploadEntity)

    @Query("UPDATE pending_uploads SET status = :status, attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE status IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_uploads WHERE status = 'DONE'")
    suspend fun clearDone()
}

@Dao
interface SyncErrorDao {

    @androidx.room.Insert
    suspend fun insert(error: SyncErrorEntity)

    @Query("SELECT * FROM sync_errors ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SyncErrorEntity>>

    /** Giữ lại 7 ngày gần nhất, tránh bảng log phình vô hạn. */
    @Query("DELETE FROM sync_errors WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
