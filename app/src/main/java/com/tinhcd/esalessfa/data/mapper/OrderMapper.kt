package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.network.dto.OrderDetailDownloadDto
import com.tinhcd.esalessfa.core.network.dto.OrderDownloadDto

/**
 * Đơn hàng tải ngược về luôn ở trạng thái SYNCED.
 *
 * Nếu server trả về đơn này thì theo định nghĩa nó đã nằm trên server. Điều đó
 * cũng tự chữa được một tình huống khó: đơn upload thành công nhưng phản hồi bị
 * mất giữa đường nên client vẫn để PENDING — lượt tải sau sẽ đưa nó về SYNCED
 * thay vì gửi lại mãi.
 *
 * Đơn chưa từng gửi thành công thì không có trên server nên không quay về, do
 * đó không có nguy cơ ghi đè nhầm trạng thái PENDING của đơn đang chờ.
 */
fun OrderDownloadDto.toEntity(clientCreatedAt: Long) = OrderEntity(
    id = id,
    orderNo = orderNo,
    customerId = customerId,
    salespersonId = salespersonId,
    visitId = visitId,
    branchId = branchId,
    orderDate = orderDate,
    deliveryDate = deliveryDate,
    status = status,
    subTotal = subTotal,
    discountAmount = discountAmount,
    manualDiscount = manualDiscount,
    netAmount = netAmount,
    vatAmount = vatAmount,
    totalAmount = totalAmount,
    note = note,
    reasonCode = reasonCode,
    syncStatus = SyncStatus.SYNCED,
    clientCreatedAt = clientCreatedAt,
    serverAckAt = clientCreatedAt,
)

fun OrderDetailDownloadDto.toEntity() = OrderDetailEntity(
    id = id,
    orderId = orderId,
    lineNo = lineNo,
    productId = productId,
    uomCode = uomCode,
    qty = qty,
    conversionRate = conversionRate,
    baseQty = baseQty,
    price = price,
    grossAmount = grossAmount,
    discountAmount = discountAmount,
    netAmount = netAmount,
    vatRate = vatRate,
    vatAmount = vatAmount,
    lineAmount = lineAmount,
    isFreeItem = isFreeItem,
    promotionId = promotionId,
)
