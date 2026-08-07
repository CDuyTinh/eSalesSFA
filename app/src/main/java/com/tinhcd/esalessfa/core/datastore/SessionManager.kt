package com.tinhcd.esalessfa.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tinhcd.esalessfa.domain.repository.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sfa_session")

/**
 * Hiện thực [SessionStore] bằng DataStore Preferences.
 *
 * Cố ý KHÔNG lưu access token ở đây — supabase-kt tự quản lý và tự refresh
 * token. Lưu thêm một bản sao chỉ tạo ra hai nguồn sự thật rồi lệch nhau.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionStore {

    override val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    /**
     * Ngày sync gần nhất, dạng ISO (`2026-08-07`).
     *
     * Lưu chuỗi ISO chứ không lưu epoch millis: mốc so sánh là NGÀY theo lịch
     * của thiết bị, nên một con số tuyệt đối lại phải quy đổi qua múi giờ mỗi
     * lần đọc. Chuỗi hỏng hoặc từ phiên bản cũ thì coi như chưa sync — cùng lắm
     * là chạy thừa một lượt, còn hơn bỏ qua lượt cần chạy.
     */
    override val lastSyncDate: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    override suspend fun saveUserId(id: String) {
        context.dataStore.edit { it[KEY_USER_ID] = id }
    }

    override suspend fun markSyncCompleted(date: LocalDate) {
        context.dataStore.edit { it[KEY_LAST_SYNC_DATE] = date.toString() }
    }

    /** Đăng xuất: xoá sạch để user sau không thấy dữ liệu của user trước. */
    override suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_LAST_SYNC_DATE = stringPreferencesKey("last_sync_date")
    }
}
