package com.tinhcd.esalessfa.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO tương ứng bảng `app_configs` trên Supabase.
 *
 * DTO cố tình tách khỏi Room Entity và Domain model: tên cột server có thể đổi
 * mà không kéo theo migration Room, và ngược lại.
 */
@Serializable
data class AppConfigDto(
    @SerialName("code") val code: String,
    @SerialName("value") val value: String,
    @SerialName("data_type") val dataType: String,
    @SerialName("row_version") val rowVersion: Long,
    @SerialName("is_deleted") val isDeleted: Boolean,
)
