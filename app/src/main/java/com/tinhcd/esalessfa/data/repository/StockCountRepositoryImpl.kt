package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.dao.StockCountDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountEntity
import com.tinhcd.esalessfa.domain.model.stock.StockCountLine
import com.tinhcd.esalessfa.domain.repository.StockCountRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockCountRepositoryImpl @Inject constructor(
    private val dao: StockCountDao,
    private val salespersonDao: SalespersonDao,
    private val visitDao: VisitDao,
) : StockCountRepository {

    override suspend fun linesFor(customerId: String): List<StockCountLine> =
        dao.productsToCount(customerId).map { row ->
            StockCountLine(
                productId = row.productId,
                productCode = row.productCode,
                productName = row.productName,
                uomCode = row.baseUom,
                qty = null,
                prevQty = row.prevBaseQty,
            )
        }

    override suspend fun save(
        customerId: String,
        lines: List<StockCountLine>,
        note: String?,
    ): String {
        val salesperson = requireNotNull(salespersonDao.getCurrent()) {
            "Chưa có hồ sơ nhân viên — cần đồng bộ trước khi kiểm kê"
        }
        val date = today()
        val now = System.currentTimeMillis()

        // Kiểm kê lại trong ngày thì ghi đè phiếu cũ thay vì tạo phiếu thứ hai —
        // hai phiếu cùng ngày làm báo cáo tồn không biết lấy số nào.
        val existing = dao.findToday(customerId, date)
        val id = existing?.id ?: UUID.randomUUID().toString()

        val header = StockCountEntity(
            id = id,
            customerId = customerId,
            salespersonId = salesperson.id,
            // Gắn với lượt ghé đang mở nếu có, để báo cáo truy được kiểm kê
            // thuộc lần viếng thăm nào.
            visitId = visitDao.getOpenVisit(customerId, date)?.id,
            countDate = date,
            note = note,
            syncStatus = SyncStatus.PENDING,
            clientCreatedAt = existing?.clientCreatedAt ?: now,
        )

        val details = lines
            .filter { it.isCounted }
            .map { line ->
                StockCountDetailEntity(
                    id = UUID.randomUUID().toString(),
                    stockCountId = id,
                    productId = line.productId,
                    uomCode = line.uomCode,
                    qty = line.qty!!,
                    // Lưới nhập theo đơn vị gốc nên hệ số quy đổi luôn là 1.
                    baseQty = line.qty,
                    prevBaseQty = line.prevQty,
                    suggestQty = line.suggestQty,
                )
            }

        dao.save(header, details)
        return id
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
