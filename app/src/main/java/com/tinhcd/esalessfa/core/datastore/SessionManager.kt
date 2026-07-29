package com.tinhcd.esalessfa.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sfa_session")

/**
 * Trạng thái phiên làm việc tồn tại qua các lần mở app.
 *
 * Cố ý KHÔNG lưu access token ở đây — supabase-kt tự quản lý và tự refresh
 * token. Lưu thêm một bản sao chỉ tạo ra hai nguồn sự thật rồi lệch nhau.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    /**
     * Đã hoàn tất lượt sync đầu tiên chưa.
     *
     * Quyết định điều hướng sau đăng nhập: chưa sync thì vào thẳng màn đồng bộ,
     * vì mọi màn hình khác đều đọc từ Room và sẽ trống trơn.
     */
    val hasCompletedFirstSync: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FIRST_SYNC_DONE] ?: false }

    suspend fun currentUserId(): String? = userId.first()

    suspend fun saveUserId(id: String) {
        context.dataStore.edit { it[KEY_USER_ID] = id }
    }

    suspend fun markFirstSyncCompleted() {
        context.dataStore.edit { it[KEY_FIRST_SYNC_DONE] = true }
    }

    /** Đăng xuất: xoá sạch để user sau không thấy dữ liệu của user trước. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_FIRST_SYNC_DONE = booleanPreferencesKey("first_sync_done")
    }
}
