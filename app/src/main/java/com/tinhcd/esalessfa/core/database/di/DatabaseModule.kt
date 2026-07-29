package com.tinhcd.esalessfa.core.database.di

import android.content.Context
import androidx.room.Room
import com.tinhcd.esalessfa.core.database.SfaDatabase
import com.tinhcd.esalessfa.core.database.dao.AppConfigDao
import com.tinhcd.esalessfa.core.database.dao.CustomerDao
import com.tinhcd.esalessfa.core.database.dao.MasterWriteDao
import com.tinhcd.esalessfa.core.database.dao.OrderDao
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.ProductDao
import com.tinhcd.esalessfa.core.database.dao.PromotionDao
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.dao.SyncErrorDao
import com.tinhcd.esalessfa.core.database.dao.SyncStateDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SfaDatabase =
        Room.databaseBuilder(context, SfaDatabase::class.java, SfaDatabase.NAME)
            // Cố ý KHÔNG dùng fallbackToDestructiveMigration: đổi schema mà quên
            // viết migration sẽ xoá sạch giao dịch chưa sync của nhân viên ngoài
            // thị trường. Thà crash lúc dev còn hơn mất dữ liệu thật.
            .build()

    @Provides fun provideMasterWriteDao(db: SfaDatabase): MasterWriteDao = db.masterWriteDao()
    @Provides fun provideAppConfigDao(db: SfaDatabase): AppConfigDao = db.appConfigDao()
    @Provides fun provideCustomerDao(db: SfaDatabase): CustomerDao = db.customerDao()
    @Provides fun provideProductDao(db: SfaDatabase): ProductDao = db.productDao()
    @Provides fun providePromotionDao(db: SfaDatabase): PromotionDao = db.promotionDao()
    @Provides fun provideVisitDao(db: SfaDatabase): VisitDao = db.visitDao()
    @Provides fun provideOrderDao(db: SfaDatabase): OrderDao = db.orderDao()
    @Provides fun provideSalespersonDao(db: SfaDatabase): SalespersonDao = db.salespersonDao()
    @Provides fun provideSyncStateDao(db: SfaDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun providePendingUploadDao(db: SfaDatabase): PendingUploadDao = db.pendingUploadDao()
    @Provides fun provideSyncErrorDao(db: SfaDatabase): SyncErrorDao = db.syncErrorDao()
}
