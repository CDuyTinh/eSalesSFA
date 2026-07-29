package com.tinhcd.esalessfa.data.di

import com.tinhcd.esalessfa.data.repository.AuthRepositoryImpl
import com.tinhcd.esalessfa.data.repository.SyncRepositoryImpl
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Nối interface ở domain với hiện thực ở data.
 *
 * Nhờ @Binds, mọi nơi chỉ inject [SyncRepository]; đổi hiện thực chỉ sửa một
 * dòng ở đây.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
