package com.tinhcd.esalessfa.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Điểm vào duy nhất để khởi động sync.
 *
 * Mọi nơi trong app đều gọi qua đây, không tự enqueue work — nhờ vậy chính sách
 * chống trùng và retry chỉ tồn tại ở một chỗ.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Tải master data.
     *
     * [ExistingWorkPolicy.KEEP] là mấu chốt chống sync trùng: bấm nút nhiều lần
     * hay app tự kích hoạt trong lúc đang chạy đều không tạo thêm lượt nào.
     * Đây chính là vai trò của `Lock.tryLock` trong bản Java cũ, nhưng bền hơn vì
     * WorkManager giữ trạng thái qua cả lần app bị kill.
     *
     * Truyền [force] = true khi user chủ động bấm đồng bộ và muốn xếp hàng chờ
     * lượt đang chạy xong.
     */
    fun startDownload(force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // Mất sóng giữa đồng là chuyện thường ngoài thị trường. Backoff giãn
            // dần thay vì đập liên tục vào server và ngốn pin.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WORK_DOWNLOAD,
            if (force) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload() = workManager.cancelUniqueWork(WORK_DOWNLOAD)

    /** Trạng thái lượt sync hiện tại để UI hiển thị tiến trình. */
    fun observeDownload(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_DOWNLOAD)
            .map { list -> list.lastOrNull() }

    private companion object {
        const val WORK_DOWNLOAD = "sync_download"
    }
}
