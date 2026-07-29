package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.dao.OrderDao
import com.tinhcd.esalessfa.core.database.dao.ProductDao
import com.tinhcd.esalessfa.core.database.dao.PromotionDao
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderPromotionEntity
import com.tinhcd.esalessfa.data.mapper.toDomain
import com.tinhcd.esalessfa.data.mapper.toDomainProgram
import com.tinhcd.esalessfa.domain.model.Product
import com.tinhcd.esalessfa.domain.promotion.OrderCalculator
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram
import com.tinhcd.esalessfa.domain.promotion.model.PromotionResult
import com.tinhcd.esalessfa.domain.repository.OrderRepository
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import com.tinhcd.esalessfa.domain.repository.PromotionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
) : ProductRepository {

    override fun search(query: String): Flow<List<Product>> =
        productDao.observeAll().map { products ->
            products
                .filter {
                    query.isBlank() ||
                        it.nameSearch?.contains(query, ignoreCase = true) == true ||
                        it.code.contains(query, ignoreCase = true)
                }
                .take(SEARCH_LIMIT)
                .map { it.toDomain(emptyList()) }
        }

    override suspend fun getById(id: String): Product? =
        productDao.findById(id)?.toDomain(productDao.getUoms(id))

    override suspend fun getPrice(
        priceGroupId: String,
        productId: String,
        uomCode: String,
    ): Long? = productDao.getPrice(priceGroupId, productId, uomCode, today())?.price

    private companion object {
        /** Danh sách chọn sản phẩm chỉ cần vài chục dòng đầu; user gõ tìm để thu hẹp. */
        const val SEARCH_LIMIT = 100
    }
}

@Singleton
class PromotionRepositoryImpl @Inject constructor(
    private val promotionDao: PromotionDao,
) : PromotionRepository {

    override suspend fun activePrograms(): List<PromotionProgram> {
        val programs = promotionDao.getActivePrograms(today())
        if (programs.isEmpty()) return emptyList()

        val ids = programs.map { it.id }
        // Gom bậc và sản phẩm bằng HAI truy vấn cho tất cả chương trình, thay vì
        // hai truy vấn cho MỖI chương trình (N+1 với 15 chương trình = 30 lượt).
        val breaks = promotionDao.getBreaks(ids).groupBy { it.programId }
        val items = promotionDao.getItems(ids).groupBy { it.programId }

        return programs.mapNotNull { program ->
            program.toDomainProgram(
                breaks = breaks[program.id].orEmpty(),
                items = items[program.id].orEmpty(),
            )
        }
    }
}

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val orderDao: OrderDao,
    private val salespersonDao: SalespersonDao,
    private val customerDao: com.tinhcd.esalessfa.core.database.dao.CustomerDao,
) : OrderRepository {

    override suspend fun confirmOrder(
        customerId: String,
        lines: List<OrderLine>,
        result: PromotionResult,
        note: String?,
    ): String {
        val salesperson = requireNotNull(salespersonDao.getCurrent()) {
            "Chưa có hồ sơ nhân viên — cần đồng bộ trước khi đặt hàng"
        }
        val customer = requireNotNull(customerDao.getById(customerId)) {
            "Không tìm thấy khách hàng $customerId"
        }

        val date = today()
        val seq = orderDao.countByDate(salesperson.id, date) + 1
        val orderNo = "%s%s%03d".format(salesperson.code, date.replace("-", ""), seq)

        val totals = OrderCalculator.totals(lines, result)
        // id do CLIENT sinh khi đang offline — điều kiện để sync-upload idempotent.
        val orderId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val order = OrderEntity(
            id = orderId,
            orderNo = orderNo,
            customerId = customerId,
            salespersonId = salesperson.id,
            visitId = null,
            branchId = customer.branchId,
            orderDate = date,
            deliveryDate = null,
            status = "CONFIRMED",
            subTotal = totals.subTotal,
            discountAmount = totals.discountAmount,
            manualDiscount = result.lineDiscounts.filter { it.isManual }.sumOf { it.amount },
            netAmount = totals.netAmount,
            vatAmount = totals.vatAmount,
            totalAmount = totals.totalAmount,
            note = note,
            reasonCode = null,
            syncStatus = SyncStatus.PENDING,
            clientCreatedAt = now,
        )

        val details = lines.map { line ->
            val lineDiscount = result.discountForLine(line.lineNo)
            val net = (line.grossAmount - lineDiscount).coerceAtLeast(0)
            val vat = com.tinhcd.esalessfa.domain.promotion.model.MoneyMath
                .percentOf(net, line.vatRate)

            OrderDetailEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                lineNo = line.lineNo,
                productId = line.productId,
                uomCode = line.uomCode,
                qty = line.qty,
                // Snapshot: giá và hệ số quy đổi tại thời điểm đặt. Không join lại
                // price_lists khi xem đơn cũ, vì giá đổi là lịch sử hiển thị sai.
                conversionRate = line.conversionRate,
                baseQty = line.baseQty,
                price = line.unitPrice,
                grossAmount = line.grossAmount,
                discountAmount = lineDiscount,
                netAmount = net,
                vatRate = line.vatRate,
                vatAmount = vat,
                lineAmount = net + vat,
                isFreeItem = false,
                promotionId = null,
            )
        }

        // Hàng tặng thành dòng riêng giá 0, nối tiếp số dòng của hàng bán.
        val freeDetails = result.freeItems.mapIndexed { index, free ->
            OrderDetailEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                lineNo = lines.size + index + 1,
                productId = free.productId,
                uomCode = "LE",
                qty = free.qty,
                conversionRate = 1.0,
                baseQty = free.qty,
                price = 0,
                grossAmount = 0,
                discountAmount = 0,
                netAmount = 0,
                vatRate = 0.0,
                vatAmount = 0,
                lineAmount = 0,
                isFreeItem = true,
                promotionId = free.programId,
            )
        }

        val promotions = result.lineDiscounts.map { discount ->
            OrderPromotionEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                orderDetailId = null,
                programId = discount.programId,
                breakId = discount.breakId,
                applyTimes = discount.applyTimes,
                discountAmount = discount.amount,
                freeQty = 0.0,
                isManual = discount.isManual,
            )
        }

        orderDao.saveOrder(order, details + freeDetails, promotions)
        return orderNo
    }
}
