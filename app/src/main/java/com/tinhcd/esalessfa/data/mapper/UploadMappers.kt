package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderPromotionEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.VisitEntity
import com.tinhcd.esalessfa.core.network.dto.OrderDetailUploadBody
import com.tinhcd.esalessfa.core.network.dto.OrderPromotionUploadBody
import com.tinhcd.esalessfa.core.network.dto.OrderUploadBody
import com.tinhcd.esalessfa.core.network.dto.VisitUploadBody
import java.time.Instant

/**
 * Room lưu thời điểm bằng epoch millis; cột Postgres là timestamptz nên phải
 * chuyển sang ISO-8601 trước khi gửi, nếu không server sẽ từ chối cả batch.
 */
private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

fun OrderEntity.toUploadBody() = OrderUploadBody(
    id = id,
    orderNo = orderNo,
    customerId = customerId,
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
    clientCreatedAt = clientCreatedAt.toIso(),
)

fun OrderDetailEntity.toUploadBody() = OrderDetailUploadBody(
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

fun OrderPromotionEntity.toUploadBody() = OrderPromotionUploadBody(
    id = id,
    orderId = orderId,
    orderDetailId = orderDetailId,
    programId = programId,
    breakId = breakId,
    applyTimes = applyTimes,
    discountAmount = discountAmount,
    freeQty = freeQty,
    isManual = isManual,
)

fun VisitEntity.toUploadBody() = VisitUploadBody(
    id = id,
    customerId = customerId,
    visitDate = visitDate,
    isInRoute = isInRoute,
    checkInAt = checkInAt.toIso(),
    checkInLat = checkInLat,
    checkInLng = checkInLng,
    checkInAccuracy = checkInAccuracy,
    checkInDistance = checkInDistance,
    checkOutAt = checkOutAt?.toIso(),
    checkOutLat = checkOutLat,
    checkOutLng = checkOutLng,
    checkOutDistance = checkOutDistance,
    durationMinutes = durationMinutes,
    reasonCode = reasonCode,
    note = note,
    isMockLocation = isMockLocation,
    batteryPct = batteryPct,
    deviceId = deviceId,
    clientCreatedAt = clientCreatedAt.toIso(),
)
