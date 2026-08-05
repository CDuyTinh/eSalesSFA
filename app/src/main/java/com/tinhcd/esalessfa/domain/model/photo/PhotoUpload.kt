package com.tinhcd.esalessfa.domain.model.photo

/**
 * Kết quả một lượt đẩy ảnh, đủ để worker quyết định thử lại hay thôi.
 */
data class PhotoUploadOutcome(
    val uploaded: Int,
    /** Còn ảnh lỗi có thể thử lại (mất mạng, server 5xx). */
    val hasRetryableFailure: Boolean,
)
