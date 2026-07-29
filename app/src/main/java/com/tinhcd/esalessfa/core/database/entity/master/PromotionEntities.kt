package com.tinhcd.esalessfa.core.database.entity.master

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chương trình khuyến mãi.
 *
 * [priority] quyết định thứ tự chạy trong pipeline của engine — chương trình
 * priority nhỏ chạy trước và có thể loại trừ chương trình sau qua
 * [excludeProgramCodes].
 */
@Entity(
    tableName = "promotion_programs",
    indices = [
        Index("code", unique = true),
        Index(value = ["fromDate", "toDate", "priority"]),
    ],
)
data class PromotionProgramEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    /** QTY_TIER | AMOUNT_TIER | FREE_ITEM | COMBO_BUNDLE | MANUAL */
    val promoType: String,
    /** LINE | GROUP | DOCUMENT — áp trên từng dòng, nhóm dòng, hay toàn đơn. */
    val applyLevel: String,
    /** PERCENT | AMOUNT | FREE_ITEM */
    val discountKind: String,
    /** false = user tự chọn trong popup thay vì hệ thống tự áp. */
    val isAutoApply: Boolean,
    /** true = cộng dồn mọi bậc thoả; false = chỉ lấy bậc cao nhất. */
    val isMultiLevel: Boolean,
    val priority: Int,
    val fromDate: String,
    val toDate: String,
    /** null = không giới hạn ngân sách. */
    val budgetAmount: Long?,
    val usedAmount: Long,
    /** "KM01;KM02" — SP đã hưởng chương trình này thì không hưởng các mã liệt kê. */
    val excludeProgramCodes: String?,
)

/**
 * Bậc khuyến mãi: đạt [minQty] hoặc [minAmount] thì được hưởng mức tương ứng.
 *
 * Một bậc chỉ dùng một trong hai điều kiện; cái còn lại để null.
 */
@Entity(
    tableName = "promotion_breaks",
    indices = [Index(value = ["programId", "breakLevel"], unique = true)],
)
data class PromotionBreakEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val breakLevel: Int,
    val minQty: Double?,
    val minAmount: Long?,
    /** 0.05 = 5%. */
    val discountPct: Double,
    /** Tiền tặng cho MỘT suất, không phải tổng. */
    val discountAmount: Long,
    /** Số lượng tặng cho MỘT suất. */
    val freeQty: Double,
    /** null = không giới hạn số suất. */
    val maxApplyTimes: Int?,
)

/**
 * Sản phẩm tham gia chương trình.
 *
 * [bundleGroup] dùng cho combo bộ: các SP cùng group là một "chân" của bộ.
 * Thiếu bất kỳ chân nào thì cả bộ không được tính.
 */
@Entity(
    tableName = "promotion_items",
    indices = [
        Index(value = ["programId", "itemRole"]),
        Index("productId"),
    ],
)
data class PromotionItemEntity(
    @PrimaryKey val id: String,
    val programId: String,
    /** null = áp cho mọi bậc của chương trình. */
    val breakId: String?,
    val productId: String,
    /** BUY = sản phẩm phải mua; FREE = sản phẩm được tặng. */
    val itemRole: String,
    val bundleGroup: String?,
    val requiredQty: Double,
    val uomCode: String?,
    /** Tồn kho hàng tặng còn lại; hết thì chương trình không áp được. */
    val freeStockQty: Double?,
)
