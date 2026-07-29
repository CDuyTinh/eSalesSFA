package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.transaction.OrderDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.OrderPromotionEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountDetailEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.StockCountEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyAnswerEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyPhotoEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.VisitEntity
import com.tinhcd.esalessfa.core.network.dto.OrderDetailUploadBody
import com.tinhcd.esalessfa.core.network.dto.OrderPromotionUploadBody
import com.tinhcd.esalessfa.core.network.dto.OrderUploadBody
import com.tinhcd.esalessfa.core.network.dto.StockCountDetailUploadBody
import com.tinhcd.esalessfa.core.network.dto.StockCountUploadBody
import com.tinhcd.esalessfa.core.network.dto.SurveyAnswerUploadBody
import com.tinhcd.esalessfa.core.network.dto.SurveyPhotoUploadBody
import com.tinhcd.esalessfa.core.network.dto.SurveyUploadBody
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

fun StockCountEntity.toUploadBody() = StockCountUploadBody(
    id = id,
    customerId = customerId,
    visitId = visitId,
    countDate = countDate,
    note = note,
    clientCreatedAt = clientCreatedAt.toIso(),
)

fun StockCountDetailEntity.toUploadBody() = StockCountDetailUploadBody(
    id = id,
    stockCountId = stockCountId,
    productId = productId,
    uomCode = uomCode,
    qty = qty,
    baseQty = baseQty,
    prevBaseQty = prevBaseQty,
    suggestQty = suggestQty,
)

fun SurveyEntity.toUploadBody() = SurveyUploadBody(
    id = id,
    surveyTypeId = surveyTypeId,
    customerId = customerId,
    visitId = visitId,
    surveyDate = surveyDate,
    totalScore = totalScore,
    isPassed = isPassed,
    note = note,
    clientCreatedAt = clientCreatedAt.toIso(),
)

fun SurveyAnswerEntity.toUploadBody() = SurveyAnswerUploadBody(
    id = id,
    surveyId = surveyId,
    questionId = questionId,
    optionId = optionId,
    answerText = answerText,
    answerValue = answerValue,
    answerBool = answerBool,
    score = score,
)

/**
 * Chỉ gọi được khi storagePath đã có giá trị — SurveyResultDao.getPending() lọc
 * sẵn những bài còn ảnh chưa upload xong.
 */
fun SurveyPhotoEntity.toUploadBody() = SurveyPhotoUploadBody(
    id = id,
    surveyId = surveyId,
    questionId = questionId,
    storagePath = requireNotNull(storagePath) { "Ảnh $id chưa upload xong" },
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt.toIso(),
    fileSize = fileSize,
)
