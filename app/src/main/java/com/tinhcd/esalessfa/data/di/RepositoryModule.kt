package com.tinhcd.esalessfa.data.di

import com.tinhcd.esalessfa.data.repository.AuthRepositoryImpl
import com.tinhcd.esalessfa.data.repository.CatalogRepositoryImpl
import com.tinhcd.esalessfa.data.repository.CustomerRepositoryImpl
import com.tinhcd.esalessfa.data.repository.OrderRepositoryImpl
import com.tinhcd.esalessfa.data.repository.PhotoUploadRepositoryImpl
import com.tinhcd.esalessfa.data.repository.ProductRepositoryImpl
import com.tinhcd.esalessfa.data.repository.PromotionRepositoryImpl
import com.tinhcd.esalessfa.data.repository.SalespersonRepositoryImpl
import com.tinhcd.esalessfa.data.repository.ReportRepositoryImpl
import com.tinhcd.esalessfa.data.repository.StockCountRepositoryImpl
import com.tinhcd.esalessfa.data.repository.SurveyRepositoryImpl
import com.tinhcd.esalessfa.data.repository.SyncRepositoryImpl
import com.tinhcd.esalessfa.data.repository.VisitRepositoryImpl
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.CatalogRepository
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.OrderRepository
import com.tinhcd.esalessfa.domain.repository.PhotoUploadRepository
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import com.tinhcd.esalessfa.domain.repository.PromotionRepository
import com.tinhcd.esalessfa.domain.repository.SalespersonRepository
import com.tinhcd.esalessfa.domain.repository.ReportRepository
import com.tinhcd.esalessfa.domain.repository.StockCountRepository
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Nối interface ở domain với hiện thực ở data.
 *
 * Tầng feature chỉ inject interface; đổi hiện thực chỉ sửa một dòng ở đây.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindCustomerRepository(impl: CustomerRepositoryImpl): CustomerRepository

    @Binds @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds @Singleton
    abstract fun bindSalespersonRepository(impl: SalespersonRepositoryImpl): SalespersonRepository

    @Binds @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds @Singleton
    abstract fun bindPromotionRepository(impl: PromotionRepositoryImpl): PromotionRepository

    @Binds @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds @Singleton
    abstract fun bindVisitRepository(impl: VisitRepositoryImpl): VisitRepository

    @Binds @Singleton
    abstract fun bindStockCountRepository(impl: StockCountRepositoryImpl): StockCountRepository

    @Binds @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository

    @Binds @Singleton
    abstract fun bindSurveyRepository(impl: SurveyRepositoryImpl): SurveyRepository

    @Binds @Singleton
    abstract fun bindPhotoUploadRepository(
        impl: PhotoUploadRepositoryImpl,
    ): PhotoUploadRepository
}
