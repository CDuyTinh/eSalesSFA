package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.ProductEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductUomEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionBreakEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionItemEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionProgramEntity
import com.tinhcd.esalessfa.domain.model.Product
import com.tinhcd.esalessfa.domain.model.ProductUom
import com.tinhcd.esalessfa.domain.promotion.model.ApplyLevel
import com.tinhcd.esalessfa.domain.promotion.model.DiscountKind
import com.tinhcd.esalessfa.domain.promotion.model.ItemRole
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionBreak
import com.tinhcd.esalessfa.domain.promotion.model.PromotionItem
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram

fun ProductEntity.toDomain(uoms: List<ProductUomEntity>) = Product(
    id = id,
    code = code,
    name = name,
    baseUom = baseUom,
    vatRate = vatRate,
    imageUrl = imageUrl,
    uoms = uoms.map { ProductUom(it.uomCode, it.conversionRate, it.isDefaultSale) },
)

/**
 * Entity -> domain model của engine.
 *
 * Trả null khi dữ liệu không dùng được, thay vì ném lỗi: một chương trình cấu
 * hình sai trên server không được làm sập cả màn đặt hàng của nhân viên.
 */
fun PromotionProgramEntity.toDomainProgram(
    breaks: List<PromotionBreakEntity>,
    items: List<PromotionItemEntity>,
): PromotionProgram? {
    val type = runCatching { PromoType.valueOf(promoType) }.getOrNull() ?: return null
    val level = runCatching { ApplyLevel.valueOf(applyLevel) }.getOrNull() ?: ApplyLevel.LINE
    val kind = runCatching { DiscountKind.valueOf(discountKind) }.getOrNull() ?: return null

    val domainBreaks = breaks.mapNotNull { it.toDomainBreak() }
    if (domainBreaks.isEmpty()) return null

    return PromotionProgram(
        id = id,
        code = code,
        name = name,
        type = type,
        applyLevel = level,
        discountKind = kind,
        isAutoApply = isAutoApply,
        isMultiLevel = isMultiLevel,
        priority = priority,
        remainingBudget = budgetAmount?.let { (it - usedAmount).coerceAtLeast(0) },
        excludeProgramCodes = excludeProgramCodes
            ?.split(';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty(),
        breaks = domainBreaks,
        items = items.mapNotNull { it.toDomainItem() },
    )
}

/**
 * Bậc thiếu cả hai điều kiện bị loại bỏ.
 *
 * PromotionBreak có require() chặn trường hợp này vì bậc không điều kiện sẽ áp
 * cho MỌI đơn hàng. Ở đây lọc trước để dữ liệu bẩn không làm crash app.
 */
private fun PromotionBreakEntity.toDomainBreak(): PromotionBreak? {
    if (minQty == null && minAmount == null) return null
    return PromotionBreak(
        id = id,
        level = breakLevel,
        minQty = minQty,
        minAmount = minAmount,
        discountPct = discountPct,
        discountAmount = discountAmount,
        freeQty = freeQty,
        maxApplyTimes = maxApplyTimes,
    )
}

private fun PromotionItemEntity.toDomainItem(): PromotionItem? {
    val role = runCatching { ItemRole.valueOf(itemRole) }.getOrNull() ?: return null
    return PromotionItem(
        productId = productId,
        role = role,
        bundleGroup = bundleGroup,
        requiredQty = requiredQty,
        freeStockQty = freeStockQty,
    )
}
