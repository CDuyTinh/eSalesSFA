package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
import com.tinhcd.esalessfa.core.database.entity.master.UomEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ghi dữ liệu master từ sync xuống.
 *
 * Dùng @Upsert thay vì Insert(REPLACE): REPLACE thực chất là DELETE + INSERT, sẽ
 * kích hoạt ON DELETE CASCADE và xoá nhầm dữ liệu con.
 */
@Dao
interface MasterWriteDao {

    @Upsert suspend fun upsertAppConfigs(items: List<AppConfigEntity>)
    @Upsert suspend fun upsertUoms(items: List<UomEntity>)
    @Upsert suspend fun upsertBranches(items: List<BranchEntity>)
    @Upsert suspend fun upsertChannels(items: List<ChannelEntity>)
    @Upsert suspend fun upsertPriceGroups(items: List<PriceGroupEntity>)
    @Upsert suspend fun upsertReasonCodes(items: List<ReasonCodeEntity>)
    @Upsert suspend fun upsertSalespersons(items: List<SalespersonEntity>)
    @Upsert suspend fun upsertCustomers(items: List<CustomerEntity>)
    @Upsert suspend fun upsertRoutes(items: List<SalesRouteEntity>)
    @Upsert suspend fun upsertRouteDetails(items: List<SalesRouteDetailEntity>)
    @Upsert suspend fun upsertCategories(items: List<ProductCategoryEntity>)
    @Upsert suspend fun upsertProducts(items: List<ProductEntity>)
    @Upsert suspend fun upsertProductUoms(items: List<ProductUomEntity>)
    @Upsert suspend fun upsertPriceLists(items: List<PriceListEntity>)
    @Upsert suspend fun upsertPromotionPrograms(items: List<PromotionProgramEntity>)
    @Upsert suspend fun upsertPromotionBreaks(items: List<PromotionBreakEntity>)
    @Upsert suspend fun upsertPromotionItems(items: List<PromotionItemEntity>)

    // Server dùng soft delete; client nhận danh sách id đã xoá rồi gỡ khỏi máy.
    @Query("DELETE FROM customers WHERE id IN (:ids)")
    suspend fun deleteCustomers(ids: List<String>)

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteProducts(ids: List<String>)

    @Query("DELETE FROM price_lists WHERE id IN (:ids)")
    suspend fun deletePriceLists(ids: List<String>)

    @Query("DELETE FROM promotion_programs WHERE id IN (:ids)")
    suspend fun deletePromotionPrograms(ids: List<String>)

    @Query("DELETE FROM sales_route_details WHERE id IN (:ids)")
    suspend fun deleteRouteDetails(ids: List<String>)
}

@Dao
interface AppConfigDao {

    @Query("SELECT value FROM app_configs WHERE code = :code")
    suspend fun getValue(code: String): String?

    @Query("SELECT * FROM app_configs")
    fun observeAll(): Flow<List<AppConfigEntity>>
}

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query(
        """
        SELECT * FROM customers
        WHERE isActive = 1
          AND (:query = '' OR nameSearch LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%')
        ORDER BY name
        """
    )
    fun observeAll(query: String = ""): Flow<List<CustomerEntity>>

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    /**
     * Khách hàng thuộc tuyến của một thứ trong tuần, theo đúng thứ tự ghé.
     * Đây là truy vấn của màn hình Home.
     */
    @Query(
        """
        SELECT c.* FROM customers c
        INNER JOIN sales_route_details d ON d.customerId = c.id
        INNER JOIN sales_routes r        ON r.id = d.routeId
        WHERE r.salespersonId = :salespersonId
          AND r.dayOfWeek = :dayOfWeek
        ORDER BY d.sortOrder
        """
    )
    fun observeRouteCustomers(salespersonId: String, dayOfWeek: Int): Flow<List<CustomerEntity>>
}

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM product_uoms WHERE productId = :productId ORDER BY sortOrder")
    suspend fun getUoms(productId: String): List<ProductUomEntity>

    /** Giá hiệu lực tại [today] cho nhóm giá của khách hàng. */
    @Query(
        """
        SELECT * FROM price_lists
        WHERE priceGroupId = :priceGroupId
          AND productId    = :productId
          AND uomCode      = :uomCode
          AND fromDate <= :today AND toDate >= :today
        LIMIT 1
        """
    )
    suspend fun getPrice(
        priceGroupId: String,
        productId: String,
        uomCode: String,
        today: String,
    ): PriceListEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}

@Dao
interface PromotionDao {

    /** Chương trình còn hiệu lực, sắp theo thứ tự chạy trong pipeline. */
    @Query(
        """
        SELECT * FROM promotion_programs
        WHERE fromDate <= :today AND toDate >= :today
        ORDER BY priority
        """
    )
    suspend fun getActivePrograms(today: String): List<PromotionProgramEntity>

    @Query("SELECT * FROM promotion_breaks WHERE programId IN (:programIds) ORDER BY breakLevel")
    suspend fun getBreaks(programIds: List<String>): List<PromotionBreakEntity>

    @Query("SELECT * FROM promotion_items WHERE programId IN (:programIds)")
    suspend fun getItems(programIds: List<String>): List<PromotionItemEntity>

    @Query("SELECT COUNT(*) FROM promotion_programs")
    suspend fun count(): Int
}
