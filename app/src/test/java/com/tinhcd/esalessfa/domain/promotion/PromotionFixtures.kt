package com.tinhcd.esalessfa.domain.promotion

import com.tinhcd.esalessfa.domain.promotion.model.ApplyLevel
import com.tinhcd.esalessfa.domain.promotion.model.DiscountKind
import com.tinhcd.esalessfa.domain.promotion.model.ItemRole
import com.tinhcd.esalessfa.domain.promotion.model.OrderLine
import com.tinhcd.esalessfa.domain.promotion.model.PromoType
import com.tinhcd.esalessfa.domain.promotion.model.PromotionBreak
import com.tinhcd.esalessfa.domain.promotion.model.PromotionItem
import com.tinhcd.esalessfa.domain.promotion.model.PromotionProgram

/** Dữ liệu mẫu dùng chung cho các test khuyến mãi. */
object Fixtures {

    fun line(
        lineNo: Int = 1,
        productId: String = "P1",
        qty: Double = 1.0,
        conversionRate: Double = 1.0,
        unitPrice: Long = 100_000,
        vatRate: Double = 0.0,
    ) = OrderLine(
        lineNo = lineNo,
        productId = productId,
        uomCode = "LE",
        qty = qty,
        conversionRate = conversionRate,
        unitPrice = unitPrice,
        vatRate = vatRate,
    )

    fun qtyTier(
        code: String = "KM01",
        productIds: List<String> = listOf("P1"),
        applyLevel: ApplyLevel = ApplyLevel.LINE,
        isMultiLevel: Boolean = false,
        priority: Int = 10,
        budget: Long? = null,
        excludes: Set<String> = emptySet(),
        breaks: List<PromotionBreak> = listOf(
            PromotionBreak("b1", 1, minQty = 10.0, discountPct = 0.03),
            PromotionBreak("b2", 2, minQty = 20.0, discountPct = 0.05),
            PromotionBreak("b3", 3, minQty = 50.0, discountPct = 0.08),
        ),
    ) = PromotionProgram(
        id = code,
        code = code,
        name = code,
        type = PromoType.QTY_TIER,
        applyLevel = applyLevel,
        discountKind = DiscountKind.PERCENT,
        isMultiLevel = isMultiLevel,
        priority = priority,
        remainingBudget = budget,
        excludeProgramCodes = excludes,
        breaks = breaks,
        items = productIds.map { PromotionItem(it, ItemRole.BUY) },
    )

    fun amountTier(
        code: String = "KM04",
        priority: Int = 20,
        budget: Long? = null,
        breaks: List<PromotionBreak> = listOf(
            PromotionBreak("a1", 1, minAmount = 5_000_000, discountPct = 0.03),
            PromotionBreak("a2", 2, minAmount = 10_000_000, discountPct = 0.05),
        ),
    ) = PromotionProgram(
        id = code,
        code = code,
        name = code,
        type = PromoType.AMOUNT_TIER,
        applyLevel = ApplyLevel.DOCUMENT,
        discountKind = DiscountKind.PERCENT,
        priority = priority,
        remainingBudget = budget,
        breaks = breaks,
    )

    fun freeItem(
        code: String = "KM06",
        buyProductIds: List<String> = listOf("P1"),
        freeProductId: String = "P9",
        freeStockQty: Double? = null,
        priority: Int = 5,
        breaks: List<PromotionBreak> = listOf(
            PromotionBreak("f1", 1, minQty = 10.0, freeQty = 1.0),
        ),
    ) = PromotionProgram(
        id = code,
        code = code,
        name = code,
        type = PromoType.FREE_ITEM,
        applyLevel = ApplyLevel.LINE,
        discountKind = DiscountKind.FREE_ITEM,
        priority = priority,
        breaks = breaks,
        items = buyProductIds.map { PromotionItem(it, ItemRole.BUY) } +
            PromotionItem(freeProductId, ItemRole.FREE, freeStockQty = freeStockQty),
    )

    /** Combo 3 chân A/B/C, mỗi chân cần 1 sản phẩm cho một bộ. */
    fun combo(
        code: String = "KM09",
        discountPct: Double = 0.10,
        discountKind: DiscountKind = DiscountKind.PERCENT,
        discountAmount: Long = 0,
        priority: Int = 15,
    ) = PromotionProgram(
        id = code,
        code = code,
        name = code,
        type = PromoType.COMBO_BUNDLE,
        applyLevel = ApplyLevel.GROUP,
        discountKind = discountKind,
        priority = priority,
        breaks = listOf(
            PromotionBreak(
                "c1", 1,
                minQty = 1.0,
                discountPct = discountPct,
                discountAmount = discountAmount,
            )
        ),
        items = listOf(
            PromotionItem("PA", ItemRole.BUY, bundleGroup = "A", requiredQty = 1.0),
            PromotionItem("PB", ItemRole.BUY, bundleGroup = "B", requiredQty = 1.0),
            PromotionItem("PC", ItemRole.BUY, bundleGroup = "C", requiredQty = 1.0),
        ),
    )

    fun manual(code: String = "KM15", maxPct: Double = 0.05) = PromotionProgram(
        id = code,
        code = code,
        name = code,
        type = PromoType.MANUAL,
        applyLevel = ApplyLevel.DOCUMENT,
        discountKind = DiscountKind.PERCENT,
        priority = 99,
        breaks = listOf(PromotionBreak("m1", 1, minAmount = 0, discountPct = maxPct)),
    )
}
