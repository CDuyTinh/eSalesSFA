package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.core.common.AppResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Đã có phiên hợp lệ chưa — quyết định Splash đi Login hay đi tiếp. */
    suspend fun isLoggedIn(): Boolean

    suspend fun signIn(email: String, password: String): AppResult<String>

    /** Đăng xuất và XOÁ SẠCH dữ liệu local. */
    suspend fun signOut()

    /**
     * Hồ sơ nhân viên của user đang đăng nhập, lấy từ Room sau khi sync.
     *
     * Trả null trước lượt sync đầu tiên. App không thể query thẳng bảng
     * salespersons trên server — mọi dữ liệu nghiệp vụ chỉ đi qua Edge Function.
     */
    fun observeCurrentSalespersonId(): Flow<String?>
}
