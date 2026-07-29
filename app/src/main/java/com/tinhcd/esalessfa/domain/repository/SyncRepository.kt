package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.sync.SyncProgress
import kotlinx.coroutines.flow.Flow

/**
 * Hợp đồng đồng bộ. Định nghĩa ở domain, hiện thực ở data — tầng UI và worker
 * chỉ biết interface này.
 */
interface SyncRepository {

    /**
     * Kéo toàn bộ thay đổi master về, lặp cho tới khi server báo hết.
     *
     * Mỗi trang được ghi trong một Room transaction: mất mạng giữa chừng thì
     * trang đó hoặc vào trọn vẹn hoặc không vào gì, không để DB nửa vời.
     */
    fun downloadMasterData(): Flow<SyncProgress>

    /** Số bản ghi đang chờ đẩy lên (outbox). */
    fun observePendingCount(): Flow<Int>

    /** Thời điểm sync thành công gần nhất, null nếu chưa từng sync. */
    fun observeLastSyncedAt(): Flow<Long?>

    /** Xoá mốc version — lần sync sau tải lại toàn bộ. Dùng khi đổi user. */
    suspend fun resetSyncState()
}
