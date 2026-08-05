package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.promotion.PromotionProgram

interface PromotionRepository {
    /** Chương trình còn hiệu lực hôm nay, đã gom đủ bậc và sản phẩm. */
    suspend fun activePrograms(): List<PromotionProgram>
}
