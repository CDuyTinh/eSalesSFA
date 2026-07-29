package com.tinhcd.esalessfa.core.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Khởi động hàng đợi upload ảnh.
 *
 * Tách khỏi SyncManager vì ràng buộc khác nhau: ảnh nặng nên chỉ upload khi
 * không ở chế độ tiết kiệm pin, còn dữ liệu giao dịch thì phải lên bằng mọi giá.
 */
@Singleton
class PhotoUploadManager @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val workManager = WorkManager.getInstance(context)

    fun start() {
        val request = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
            .build()

        // KEEP: chụp liên tiếp 5 tấm không tạo 5 công việc; worker đang chạy sẽ
        // quét lại hàng đợi và nhặt luôn ảnh mới.
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "photo_upload"
    }
}
