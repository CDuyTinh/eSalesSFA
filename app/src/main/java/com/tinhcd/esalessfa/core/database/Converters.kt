package com.tinhcd.esalessfa.core.database

import androidx.room.TypeConverter

/**
 * Quy ước kiểu dữ liệu của project:
 *
 *  - Tiền      -> Long (VND, không thập phân). KHÔNG dùng Double.
 *  - Số lượng  -> Double
 *  - Thời điểm -> Long epoch millis
 *  - Ngày      -> String "yyyy-MM-dd" (so sánh chuỗi vẫn đúng thứ tự)
 *
 * Nhờ quy ước đó, chỉ còn enum cần chuyển đổi.
 */
class Converters {

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? =
        value?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() }
}
