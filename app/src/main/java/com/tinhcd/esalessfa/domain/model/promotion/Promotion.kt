package com.tinhcd.esalessfa.domain.model.promotion

/**
 * Loại chương trình khuyến mãi.
 *
 * Mỗi loại ứng với một [com.tinhcd.esalessfa.domain.model.promotion.PromotionRule]
 * riêng — thêm loại mới là thêm một rule, không sửa engine.
 */
enum class PromoType {
    /** Chiết khấu bậc theo SỐ LƯỢNG mua. */
    QTY_TIER,

    /** Chiết khấu bậc theo GIÁ TRỊ đơn. */
    AMOUNT_TIER,

    /** Mua X tặng Y. */
    FREE_ITEM,

    /** Phải mua đủ bộ (nhiều nhóm sản phẩm) mới được hưởng. */
    COMBO_BUNDLE,

    /** Nhân viên nhập tay, có trần. */
    MANUAL,
}

/** Phạm vi áp dụng: từng dòng, nhóm dòng, hay toàn đơn. */
enum class ApplyLevel { LINE, GROUP, DOCUMENT }

/** Hình thức ưu đãi. */
enum class DiscountKind { PERCENT, AMOUNT, FREE_ITEM }

enum class ItemRole { BUY, FREE }

/**
 * Một bậc khuyến mãi.
 *
 * Chỉ dùng MỘT trong hai điều kiện: [minQty] cho bậc theo số lượng, [minAmount]
 * cho bậc theo giá trị. Cái còn lại phải null — để 0 sẽ bị hiểu là "từ 0 trở lên"
 * và áp cho mọi đơn hàng.
 */
data class PromotionBreak(
    val id: String,
    val level: Int,
    val minQty: Double? = null,
    val minAmount: Long? = null,
    /** 0.05 = 5%. */
    val discountPct: Double = 0.0,
    /** Tiền tặng cho MỘT suất. */
    val discountAmount: Long = 0,
    /** Số lượng tặng cho MỘT suất. */
    val freeQty: Double = 0.0,
    /** Trần số suất; null = không giới hạn. */
    val maxApplyTimes: Int? = null,
) {
    init {
        require(minQty != null || minAmount != null) {
            "Bậc $id không có điều kiện nào — sẽ áp cho mọi đơn hàng"
        }
    }
}

/**
 * Sản phẩm tham gia chương trình.
 *
 * [bundleGroup] dùng cho combo: các sản phẩm cùng group là MỘT chân của bộ.
 * Thiếu bất kỳ chân nào thì cả bộ không tính.
 */
data class PromotionItem(
    val productId: String,
    val role: ItemRole,
    val bundleGroup: String? = null,
    val requiredQty: Double = 0.0,
    /** Tồn kho hàng tặng còn lại; null = không giới hạn. */
    val freeStockQty: Double? = null,
)

data class PromotionProgram(
    val id: String,
    val code: String,
    val name: String,
    val type: PromoType,
    val applyLevel: ApplyLevel,
    val discountKind: DiscountKind,
    /** false = user tự chọn trong popup thay vì hệ thống tự áp. */
    val isAutoApply: Boolean = true,
    /**
     * true  = cộng dồn MỌI bậc thoả điều kiện (tích luỹ bậc thang).
     * false = chỉ lấy bậc CAO NHẤT thoả điều kiện.
     */
    val isMultiLevel: Boolean = false,
    /** Số nhỏ chạy trước trong pipeline. */
    val priority: Int = 0,
    /** Ngân sách còn lại; null = không giới hạn. */
    val remainingBudget: Long? = null,
    /** Mã các chương trình bị loại trừ khi tính điều kiện của chương trình này. */
    val excludeProgramCodes: Set<String> = emptySet(),
    val breaks: List<PromotionBreak> = emptyList(),
    val items: List<PromotionItem> = emptyList(),
) {
    val buyProductIds: Set<String> = items.filter { it.role == ItemRole.BUY }.map { it.productId }.toSet()
    val freeItems: List<PromotionItem> = items.filter { it.role == ItemRole.FREE }

    /** Các chân của bộ combo, giữ nguyên thứ tự khai báo. */
    val bundleGroups: Map<String, List<PromotionItem>> =
        items.filter { it.role == ItemRole.BUY && it.bundleGroup != null }
            .groupBy { it.bundleGroup!! }
}
