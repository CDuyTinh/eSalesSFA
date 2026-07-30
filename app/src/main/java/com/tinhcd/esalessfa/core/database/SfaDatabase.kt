package com.tinhcd.esalessfa.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tinhcd.esalessfa.core.database.dao.AppConfigDao
import com.tinhcd.esalessfa.core.database.dao.CatalogQueryDao
import com.tinhcd.esalessfa.core.database.dao.CustomerDao
import com.tinhcd.esalessfa.core.database.dao.CustomerQueryDao
import com.tinhcd.esalessfa.core.database.dao.MasterWriteDao
import com.tinhcd.esalessfa.core.database.dao.OrderDao
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.ProductDao
import com.tinhcd.esalessfa.core.database.dao.PromotionDao
import com.tinhcd.esalessfa.core.database.dao.ReportDao
import com.tinhcd.esalessfa.core.database.dao.ReasonCodeDao
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.dao.StockCountDao
import com.tinhcd.esalessfa.core.database.dao.SurveyConfigDao
import com.tinhcd.esalessfa.core.database.dao.SurveyResultDao
import com.tinhcd.esalessfa.core.database.dao.SyncErrorDao
import com.tinhcd.esalessfa.core.database.dao.SyncStateDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.core.database.entity.local.PendingUploadEntity
import com.tinhcd.esalessfa.core.database.entity.local.SyncErrorEntity
import com.tinhcd.esalessfa.core.database.entity.local.SyncStateEntity
import com.tinhcd.esalessfa.core.database.entity.master.AppConfigEntity
import com.tinhcd.esalessfa.core.database.entity.master.BranchEntity
import com.tinhcd.esalessfa.core.database.entity.master.ChannelEntity
import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import com.tinhcd.esalessfa.core.database.entity.master.PriceGroupEntity
import com.tinhcd.esalessfa.core.database.entity.master.PriceListEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductCategoryEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductUomEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionBreakEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionItemEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionProgramEntity
import com.tinhcd.esalessfa.core.database.entity.master.ReasonCodeEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalesRouteDetailEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalesRouteEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalespersonEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionGroupEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionOptionEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyTypeEntity
import com.tinhcd.esalessfa.core.database.entity.master.UomEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderPromotionEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyAnswerEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyPhotoEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.VisitEntity

/**
 * Single source of truth của app.
 *
 * UI KHÔNG bao giờ đọc thẳng từ mạng — mọi màn hình quan sát Room qua Flow. Sync
 * chỉ ghi vào đây rồi UI tự cập nhật. Nhờ vậy app hoạt động y hệt nhau dù online
 * hay offline, và không có đường code nào chỉ chạy khi có mạng.
 *
 * Schema JSON được xuất ra app/schemas/ và commit vào git để review migration.
 */
@Database(
    entities = [
        // master
        AppConfigEntity::class,
        UomEntity::class,
        BranchEntity::class,
        ChannelEntity::class,
        PriceGroupEntity::class,
        ReasonCodeEntity::class,
        SalespersonEntity::class,
        CustomerEntity::class,
        SalesRouteEntity::class,
        SalesRouteDetailEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        ProductUomEntity::class,
        PriceListEntity::class,
        PromotionProgramEntity::class,
        PromotionBreakEntity::class,
        PromotionItemEntity::class,
        SurveyTypeEntity::class,
        SurveyQuestionGroupEntity::class,
        SurveyQuestionEntity::class,
        SurveyQuestionOptionEntity::class,
        // transaction
        VisitEntity::class,
        OrderEntity::class,
        OrderDetailEntity::class,
        OrderPromotionEntity::class,
        StockCountEntity::class,
        StockCountDetailEntity::class,
        SurveyEntity::class,
        SurveyAnswerEntity::class,
        SurveyPhotoEntity::class,
        // local
        SyncStateEntity::class,
        PendingUploadEntity::class,
        SyncErrorEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SfaDatabase : RoomDatabase() {

    abstract fun masterWriteDao(): MasterWriteDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun customerDao(): CustomerDao
    abstract fun customerQueryDao(): CustomerQueryDao
    abstract fun catalogQueryDao(): CatalogQueryDao
    abstract fun productDao(): ProductDao
    abstract fun promotionDao(): PromotionDao
    abstract fun reportDao(): ReportDao
    abstract fun reasonCodeDao(): ReasonCodeDao
    abstract fun salespersonDao(): SalespersonDao
    abstract fun stockCountDao(): StockCountDao
    abstract fun surveyConfigDao(): SurveyConfigDao
    abstract fun surveyResultDao(): SurveyResultDao
    abstract fun visitDao(): VisitDao
    abstract fun orderDao(): OrderDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun pendingUploadDao(): PendingUploadDao
    abstract fun syncErrorDao(): SyncErrorDao

    companion object {
        const val NAME = "esales_sfa.db"
    }
}
