package com.tinhcd.esalessfa.data.repository

import androidx.room.withTransaction
import com.tinhcd.esalessfa.core.common.dispatcher.DispatcherProvider
import com.tinhcd.esalessfa.core.database.SfaDatabase
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.dao.MasterWriteDao
import com.tinhcd.esalessfa.core.database.dao.OrderDao
import com.tinhcd.esalessfa.core.database.dao.StockCountDao
import com.tinhcd.esalessfa.core.database.dao.SurveyResultDao
import com.tinhcd.esalessfa.core.database.dao.SyncErrorDao
import com.tinhcd.esalessfa.core.database.dao.SyncStateDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.core.database.entity.local.SyncErrorEntity
import com.tinhcd.esalessfa.core.database.entity.local.SyncStateEntity
import com.tinhcd.esalessfa.core.network.api.SyncApi
import com.tinhcd.esalessfa.core.network.dto.AppConfigDto
import com.tinhcd.esalessfa.core.network.dto.BranchDto
import com.tinhcd.esalessfa.core.network.dto.ChannelDto
import com.tinhcd.esalessfa.core.network.dto.CustomerDto
import com.tinhcd.esalessfa.core.network.dto.PriceGroupDto
import com.tinhcd.esalessfa.core.network.dto.PriceListDto
import com.tinhcd.esalessfa.core.network.dto.ProductCategoryDto
import com.tinhcd.esalessfa.core.network.dto.ProductDto
import com.tinhcd.esalessfa.core.network.dto.ProductUomDto
import com.tinhcd.esalessfa.core.network.dto.PromotionBreakDto
import com.tinhcd.esalessfa.core.network.dto.PromotionItemDto
import com.tinhcd.esalessfa.core.network.dto.PromotionProgramDto
import com.tinhcd.esalessfa.core.network.dto.ReasonCodeDto
import com.tinhcd.esalessfa.core.network.dto.SalesRouteDetailDto
import com.tinhcd.esalessfa.core.network.dto.SalesRouteDto
import com.tinhcd.esalessfa.core.network.dto.OrderDetailDownloadDto
import com.tinhcd.esalessfa.core.network.dto.OrderDownloadDto
import com.tinhcd.esalessfa.core.network.dto.SalespersonDto
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionDto
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionGroupDto
import com.tinhcd.esalessfa.core.network.dto.SurveyQuestionOptionDto
import com.tinhcd.esalessfa.core.network.dto.SurveyTypeDto
import com.tinhcd.esalessfa.core.network.dto.OrderUploadDto
import com.tinhcd.esalessfa.core.network.dto.SyncDownloadRequest
import com.tinhcd.esalessfa.core.network.dto.StockCountUploadDto
import com.tinhcd.esalessfa.core.network.dto.SurveyUploadDto
import com.tinhcd.esalessfa.core.network.dto.SyncUploadRequest
import com.tinhcd.esalessfa.core.network.dto.TableChangeSet
import com.tinhcd.esalessfa.core.network.dto.UomDto
import com.tinhcd.esalessfa.data.mapper.toEntity
import com.tinhcd.esalessfa.data.mapper.toUploadBody
import com.tinhcd.esalessfa.domain.repository.SyncRepository
import com.tinhcd.esalessfa.domain.sync.SyncProgress
import com.tinhcd.esalessfa.domain.sync.SyncTables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val api: SyncApi,
    private val db: SfaDatabase,
    private val masterDao: MasterWriteDao,
    private val syncStateDao: SyncStateDao,
    private val visitDao: VisitDao,
    private val orderDao: OrderDao,
    private val stockCountDao: StockCountDao,
    private val surveyResultDao: SurveyResultDao,
    private val syncErrorDao: SyncErrorDao,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : SyncRepository {

    /**
     * Chặn hai lượt sync chạy chồng nhau trong cùng tiến trình.
     *
     * WorkManager đã chống trùng ở mức công việc bằng unique work, nhưng vẫn có
     * đường gọi trực tiếp từ UI (nút "Đồng bộ"). Mutex bịt nốt khe đó.
     */
    private val syncMutex = Mutex()

    /** Mutex riêng cho upload: tải xuống và gửi lên không cần chặn nhau. */
    private val uploadMutex = Mutex()

    override fun downloadMasterData(): Flow<SyncProgress> = flow {
        if (syncMutex.isLocked) {
            emit(SyncProgress.Failed("Đang có một lượt đồng bộ khác chạy", isRetryable = false))
            return@flow
        }

        syncMutex.withLock {
            val startedAt = System.currentTimeMillis()
            emit(SyncProgress.Started)

            var page = 0
            var totalRows = 0
            var hasMore = true

            while (hasMore) {
                page++

                // Đọc lại mốc version mỗi vòng: trang trước vừa ghi xong đã cập nhật.
                val versions = syncStateDao.getAll()
                    .associate { it.tableName to it.lastVersion }

                val response = api.download(
                    SyncDownloadRequest(
                        sessionId = UUID.randomUUID().toString(),
                        versions = versions,
                        pageSize = PAGE_SIZE,
                    )
                )

                if (!response.isSuccessful) {
                    val code = response.code()
                    emit(
                        SyncProgress.Failed(
                            message = "Máy chủ trả về lỗi $code: ${response.errorBody()?.string().orEmpty()}",
                            // 4xx là lỗi phía client (token hỏng, chưa nối nhân viên) —
                            // retry cũng vô ích. 5xx thì đáng thử lại.
                            isRetryable = code >= 500,
                        )
                    )
                    return@withLock
                }

                val body = response.body()
                if (body == null) {
                    emit(SyncProgress.Failed("Máy chủ trả về nội dung rỗng", isRetryable = true))
                    return@withLock
                }

                // Cả trang ghi trong MỘT transaction: hoặc vào trọn vẹn, hoặc
                // không gì cả. Mốc version chỉ tiến khi dữ liệu đã nằm trong DB,
                // nên mất mạng giữa chừng thì lần sau tải lại đúng phần còn thiếu.
                var pageRows = 0
                db.withTransaction {
                    body.tables.forEach { (table, changeSet) ->
                        pageRows += writeTable(table, changeSet)
                    }
                }

                totalRows += pageRows
                hasMore = body.hasMore

                emit(
                    SyncProgress.Downloading(
                        page = page,
                        rowsThisPage = pageRows,
                        totalRows = totalRows,
                        currentTable = body.tables.keys.lastOrNull(),
                    )
                )

                if (page >= MAX_PAGES) {
                    emit(
                        SyncProgress.Failed(
                            "Vượt quá $MAX_PAGES trang — nghi ngờ vòng lặp không kết thúc",
                            isRetryable = false,
                        )
                    )
                    return@withLock
                }
            }

            emit(
                SyncProgress.Completed(
                    totalRows = totalRows,
                    pages = page,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            )
        }
    }.flowOn(dispatchers.io)

    /**
     * Giải mã và ghi một bảng. Trả về số dòng đã ghi.
     *
     * Bảng lạ (server thêm mới, client chưa biết) bị bỏ qua thay vì ném lỗi —
     * app phiên bản cũ vẫn sync được sau khi server nâng cấp.
     */
    private suspend fun writeTable(table: String, changeSet: TableChangeSet): Int {
        val rows = changeSet.rows

        when (table) {
            SyncTables.APP_CONFIGS ->
                masterDao.upsertAppConfigs(rows.decode<AppConfigDto>().map { it.toEntity() })

            SyncTables.UOMS ->
                masterDao.upsertUoms(rows.decode<UomDto>().map { it.toEntity() })

            SyncTables.BRANCHES ->
                masterDao.upsertBranches(rows.decode<BranchDto>().map { it.toEntity() })

            SyncTables.CHANNELS ->
                masterDao.upsertChannels(rows.decode<ChannelDto>().map { it.toEntity() })

            SyncTables.PRICE_GROUPS ->
                masterDao.upsertPriceGroups(rows.decode<PriceGroupDto>().map { it.toEntity() })

            SyncTables.REASON_CODES ->
                masterDao.upsertReasonCodes(rows.decode<ReasonCodeDto>().map { it.toEntity() })

            SyncTables.SALESPERSONS ->
                masterDao.upsertSalespersons(rows.decode<SalespersonDto>().map { it.toEntity() })

            SyncTables.CUSTOMERS -> {
                masterDao.upsertCustomers(rows.decode<CustomerDto>().map { it.toEntity() })
                if (changeSet.deletedIds.isNotEmpty()) masterDao.deleteCustomers(changeSet.deletedIds)
            }

            SyncTables.SALES_ROUTES ->
                masterDao.upsertRoutes(rows.decode<SalesRouteDto>().map { it.toEntity() })

            SyncTables.SALES_ROUTE_DETAILS -> {
                masterDao.upsertRouteDetails(rows.decode<SalesRouteDetailDto>().map { it.toEntity() })
                if (changeSet.deletedIds.isNotEmpty()) masterDao.deleteRouteDetails(changeSet.deletedIds)
            }

            SyncTables.PRODUCT_CATEGORIES ->
                masterDao.upsertCategories(rows.decode<ProductCategoryDto>().map { it.toEntity() })

            SyncTables.PRODUCTS -> {
                masterDao.upsertProducts(rows.decode<ProductDto>().map { it.toEntity() })
                if (changeSet.deletedIds.isNotEmpty()) masterDao.deleteProducts(changeSet.deletedIds)
            }

            SyncTables.PRODUCT_UOMS ->
                masterDao.upsertProductUoms(rows.decode<ProductUomDto>().map { it.toEntity() })

            SyncTables.PRICE_LISTS -> {
                masterDao.upsertPriceLists(rows.decode<PriceListDto>().map { it.toEntity() })
                if (changeSet.deletedIds.isNotEmpty()) masterDao.deletePriceLists(changeSet.deletedIds)
            }

            SyncTables.PROMOTION_PROGRAMS -> {
                masterDao.upsertPromotionPrograms(rows.decode<PromotionProgramDto>().map { it.toEntity() })
                if (changeSet.deletedIds.isNotEmpty()) masterDao.deletePromotionPrograms(changeSet.deletedIds)
            }

            SyncTables.PROMOTION_BREAKS ->
                masterDao.upsertPromotionBreaks(rows.decode<PromotionBreakDto>().map { it.toEntity() })

            SyncTables.PROMOTION_ITEMS ->
                masterDao.upsertPromotionItems(rows.decode<PromotionItemDto>().map { it.toEntity() })

            SyncTables.SURVEY_TYPES ->
                masterDao.upsertSurveyTypes(rows.decode<SurveyTypeDto>().map { it.toEntity() })

            SyncTables.SURVEY_QUESTION_GROUPS ->
                masterDao.upsertSurveyQuestionGroups(
                    rows.decode<SurveyQuestionGroupDto>().map { it.toEntity() }
                )

            SyncTables.SURVEY_QUESTIONS ->
                masterDao.upsertSurveyQuestions(
                    rows.decode<SurveyQuestionDto>().map { it.toEntity() }
                )

            SyncTables.SURVEY_QUESTION_OPTIONS ->
                masterDao.upsertSurveyQuestionOptions(
                    rows.decode<SurveyQuestionOptionDto>().map { it.toEntity() }
                )

            SyncTables.ORDERS -> {
                val now = System.currentTimeMillis()
                masterDao.upsertOrders(rows.decode<OrderDownloadDto>().map { it.toEntity(now) })
            }

            SyncTables.ORDER_DETAILS ->
                masterDao.upsertOrderDetails(
                    rows.decode<OrderDetailDownloadDto>().map { it.toEntity() }
                )

            else -> return 0
        }

        syncStateDao.upsert(
            SyncStateEntity(
                tableName = table,
                lastVersion = changeSet.maxVersion,
                lastSyncedAt = System.currentTimeMillis(),
                rowCount = rows.size,
            )
        )
        return rows.size
    }

    private inline fun <reified T> List<JsonElement>.decode(): List<T> =
        map { json.decodeFromJsonElement(it) }

    override fun uploadPending(): Flow<SyncProgress> = flow {
        if (uploadMutex.isLocked) {
            emit(SyncProgress.Failed("Đang có một lượt gửi khác chạy", isRetryable = false))
            return@flow
        }

        // Quy tắc nghiệp vụ: còn cửa hàng đang check-in thì chưa đồng bộ lên.
        // Đơn hàng, kiểm kê và khảo sát của lượt ghé đó vẫn có thể bị sửa cho tới
        // khi check-out, gửi sớm là đẩy số liệu chưa chốt.
        //
        // Chặn ở tầng repository chứ không chỉ khoá nút: auto-upload sau mỗi lần
        // chốt đơn hay lưu phiếu cũng phải tuân theo.
        if (visitDao.countOpenVisits() > 0) {
            emit(SyncProgress.Skipped("Còn cửa hàng chưa check-out"))
            return@flow
        }

        uploadMutex.withLock {
            val startedAt = System.currentTimeMillis()
            emit(SyncProgress.Started)

            val visits = visitDao.getPending()
            val orders = orderDao.getPending()
            val stockCounts = stockCountDao.getPending()
            val surveys = surveyResultDao.getPending()
            val total = visits.size + orders.size + stockCounts.size + surveys.size

            if (total == 0) {
                emit(SyncProgress.Completed(0, 0, 0))
                return@withLock
            }

            // sessionId mới mỗi lượt là an toàn: Edge Function upsert theo id bản
            // ghi với ignoreDuplicates, nên gửi lại cùng một đơn không tạo bản
            // ghi thứ hai dù session khác.
            val sessionId = UUID.randomUUID().toString()
            val visitIds = visits.map { it.id }
            val orderIds = orders.map { it.order.id }
            val stockIds = stockCounts.map { it.header.id }
            val surveyIds = surveys.map { it.header.id }

            visitDao.markStatus(visitIds, SyncStatus.SYNCING, sessionId)
            orderDao.markStatus(orderIds, SyncStatus.SYNCING, sessionId)
            stockCountDao.markStatus(stockIds, SyncStatus.SYNCING, sessionId)
            surveyResultDao.markStatus(surveyIds, SyncStatus.SYNCING, sessionId)

            emit(SyncProgress.Uploading(sent = 0, total = total))

            val request = SyncUploadRequest(
                sessionId = sessionId,
                visits = visits.map { json.encodeToJsonElement(it.toUploadBody()) },
                orders = orders.map { row ->
                    OrderUploadDto(
                        order = json.encodeToJsonElement(row.order.toUploadBody()),
                        details = row.details.map { json.encodeToJsonElement(it.toUploadBody()) },
                        promotions = row.promotions.map { json.encodeToJsonElement(it.toUploadBody()) },
                    )
                },
                stockCounts = stockCounts.map { row ->
                    StockCountUploadDto(
                        header = json.encodeToJsonElement(row.header.toUploadBody()),
                        details = row.details.map { json.encodeToJsonElement(it.toUploadBody()) },
                    )
                },
                surveys = surveys.map { row ->
                    SurveyUploadDto(
                        header = json.encodeToJsonElement(row.header.toUploadBody()),
                        answers = row.answers.map { json.encodeToJsonElement(it.toUploadBody()) },
                        photos = row.photos.map { json.encodeToJsonElement(it.toUploadBody()) },
                    )
                },
            )

            val response = try {
                api.upload(request)
            } catch (e: Exception) {
                // Không biết server đã nhận hay chưa -> trả về PENDING để lượt sau
                // gửi lại. Bỏ qua bước này thì bản ghi kẹt SYNCING vĩnh viễn.
                revertToPending(visitIds, orderIds, stockIds, surveyIds)
                emit(SyncProgress.Failed(e.message ?: "Lỗi kết nối", isRetryable = true))
                return@withLock
            }

            if (!response.isSuccessful) {
                revertToPending(visitIds, orderIds, stockIds, surveyIds)
                val code = response.code()
                emit(
                    SyncProgress.Failed(
                        "Máy chủ trả về lỗi $code",
                        isRetryable = code >= 500,
                    )
                )
                return@withLock
            }

            val body = response.body()
            if (body == null) {
                revertToPending(visitIds, orderIds, stockIds, surveyIds)
                emit(SyncProgress.Failed("Máy chủ trả về nội dung rỗng", isRetryable = true))
                return@withLock
            }

            val now = System.currentTimeMillis()
            val accepted = body.accepted.toSet()

            visitDao.markSynced(visitIds.filter { it in accepted }, now)
            orderDao.markSynced(orderIds.filter { it in accepted }, now)
            stockCountDao.markSynced(stockIds.filter { it in accepted }, now)
            surveyResultDao.markSynced(surveyIds.filter { it in accepted }, now)

            // Bị từ chối vì lỗi nghiệp vụ: đánh dấu FAILED kèm lý do để user thấy,
            // KHÔNG đưa về PENDING vì gửi lại vẫn bị từ chối y hệt.
            body.rejected.forEach { item ->
                syncErrorDao.insert(
                    SyncErrorEntity(
                        sessionId = sessionId,
                        tableName = item.entity,
                        entityId = item.id,
                        message = "${item.code}: ${item.message}",
                        createdAt = now,
                    )
                )
                when (item.entity) {
                    "order" -> orderDao.markFailed(item.id, "${item.code}: ${item.message}")
                    "visit" -> visitDao.markFailed(item.id, "${item.code}: ${item.message}")
                    "stock_count" -> stockCountDao.markFailed(item.id, "${item.code}: ${item.message}")
                    "survey" -> surveyResultDao.markFailed(item.id, "${item.code}: ${item.message}")
                }
            }

            // Id không nằm trong cả accepted lẫn rejected -> server im lặng bỏ qua.
            // Trả về PENDING để lượt sau thử lại, thay vì để kẹt SYNCING.
            val handled = accepted + body.rejected.map { it.id }.toSet()
            revertToPending(
                visitIds.filterNot { it in handled },
                orderIds.filterNot { it in handled },
                stockIds.filterNot { it in handled },
                surveyIds.filterNot { it in handled },
            )

            emit(
                SyncProgress.Completed(
                    totalRows = accepted.size,
                    pages = 1,
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            )
        }
    }.flowOn(dispatchers.io)

    private suspend fun revertToPending(
        visitIds: List<String>,
        orderIds: List<String>,
        stockIds: List<String> = emptyList(),
        surveyIds: List<String> = emptyList(),
    ) {
        if (visitIds.isNotEmpty()) visitDao.markStatus(visitIds, SyncStatus.PENDING, null)
        if (orderIds.isNotEmpty()) orderDao.markStatus(orderIds, SyncStatus.PENDING, null)
        if (stockIds.isNotEmpty()) stockCountDao.markStatus(stockIds, SyncStatus.PENDING, null)
        if (surveyIds.isNotEmpty()) surveyResultDao.markStatus(surveyIds, SyncStatus.PENDING, null)
    }

    override fun observePendingCount(): Flow<Int> =
        combine(
            visitDao.observePendingCount(),
            orderDao.observePendingCount(),
            stockCountDao.observePendingCount(),
            surveyResultDao.observePendingCount(),
        ) { visits, orders, stocks, surveys -> visits + orders + stocks + surveys }

    override fun observeLastSyncedAt(): Flow<Long?> =
        syncStateDao.observeOldestSync().map { if (it == 0L) null else it }

    override suspend fun resetSyncState() = syncStateDao.clear()

    private companion object {
        /** Khớp trần db-max-rows của PostgREST; đặt lớn hơn cũng bị server cắt. */
        const val PAGE_SIZE = 1000

        /** Chặn vòng lặp vô hạn nếu server luôn báo has_more do lỗi logic. */
        const val MAX_PAGES = 50
    }
}
