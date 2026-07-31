package com.tinhcd.esalessfa.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Cổng đọc/ghi trạng thái phiên làm việc tồn tại qua các lần mở app.
 *
 * Cố ý KHÔNG có access token: supabase-kt tự quản lý và tự refresh token, lưu
 * thêm một bản sao chỉ tạo ra hai nguồn sự thật rồi lệch nhau.
 */
interface SessionStore {

    val userId: Flow<String?>

    /**
     * Đã hoàn tất lượt sync đầu tiên chưa.
     *
     * Quyết định điều hướng sau đăng nhập: chưa sync thì vào thẳng màn đồng bộ,
     * vì mọi màn hình khác đều đọc từ Room và sẽ trống trơn.
     */
    val hasCompletedFirstSync: Flow<Boolean>

    suspend fun saveUserId(id: String)

    suspend fun markFirstSyncCompleted()

    /** Đăng xuất: xoá sạch để user sau không thấy dữ liệu của user trước. */
    suspend fun clear()
}
