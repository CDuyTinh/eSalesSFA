package com.tinhcd.esalessfa.core.di

import com.tinhcd.esalessfa.core.datastore.SessionManager
import com.tinhcd.esalessfa.core.file.CacheReportFileStore
import com.tinhcd.esalessfa.core.location.LocationProvider
import com.tinhcd.esalessfa.core.media.PhotoUploadManager
import com.tinhcd.esalessfa.core.sync.SyncManager
import com.tinhcd.esalessfa.domain.repository.LocationSource
import com.tinhcd.esalessfa.domain.repository.PhotoUploader
import com.tinhcd.esalessfa.domain.repository.ReportFileStore
import com.tinhcd.esalessfa.domain.repository.SessionStore
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Nối cổng khai ở domain với hiện thực chạy trên nền tảng Android.
 *
 * Cùng vai trò với RepositoryModule, chỉ khác thứ được nối: bên kia là kho dữ
 * liệu, bên này là WorkManager, DataStore, GPS. Tách hai module để nhìn một chỗ
 * là biết app đụng vào những mảng nền tảng nào.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds @Singleton
    abstract fun bindSyncScheduler(impl: SyncManager): SyncScheduler

    @Binds @Singleton
    abstract fun bindSessionStore(impl: SessionManager): SessionStore

    @Binds @Singleton
    abstract fun bindLocationSource(impl: LocationProvider): LocationSource

    @Binds @Singleton
    abstract fun bindPhotoUploader(impl: PhotoUploadManager): PhotoUploader

    @Binds @Singleton
    abstract fun bindReportFileStore(impl: CacheReportFileStore): ReportFileStore
}
