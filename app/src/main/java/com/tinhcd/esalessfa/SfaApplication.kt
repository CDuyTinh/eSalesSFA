package com.tinhcd.esalessfa

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Cấu hình WorkManager thủ công để worker nhận được dependency từ Hilt.
 *
 * Đi kèm việc gỡ WorkManagerInitializer trong AndroidManifest — nếu để khởi tạo
 * tự động, WorkManager dựng trước Hilt và worker sẽ crash vì không có factory.
 */
@HiltAndroidApp
class SfaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
