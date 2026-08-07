package com.tinhcd.esalessfa.domain.repository

import java.time.LocalDate
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
     * Ngày của lượt đồng bộ gần nhất, null là chưa sync lần nào.
     *
     * Lưu ngày chứ không lưu cờ "đã sync lần đầu" vì nó trả lời được cả hai câu
     * hỏi điều hướng: chưa sync bao giờ thì mọi màn hình đều trống trơn (UI chỉ
     * đọc từ Room), còn sync từ hôm qua thì tuyến bán hàng và tồn kho của hôm
     * nay chưa có trên máy.
     */
    val lastSyncDate: Flow<LocalDate?>

    suspend fun saveUserId(id: String)

    suspend fun markSyncCompleted(date: LocalDate)

    /** Đăng xuất: xoá sạch để user sau không thấy dữ liệu của user trước. */
    suspend fun clear()
}
