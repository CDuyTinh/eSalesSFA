package com.tinhcd.esalessfa.data.repository

import com.tinhcd.esalessfa.core.common.AppError
import com.tinhcd.esalessfa.core.common.AppResult
import com.tinhcd.esalessfa.core.database.SfaDatabase
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.datastore.SessionManager
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: Auth,
    private val salespersonDao: SalespersonDao,
    private val sessionManager: SessionManager,
    private val db: SfaDatabase,
) : AuthRepository {

    override suspend fun isLoggedIn(): Boolean {
        // Chờ supabase-kt khôi phục phiên đã lưu trước khi kết luận. Bỏ bước này
        // thì mở app lúc nào cũng bị đá về màn đăng nhập.
        auth.awaitInitialization()
        return auth.currentSessionOrNull() != null
    }

    override suspend fun signIn(email: String, password: String): AppResult<String> = try {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val userId = auth.currentUserOrNull()?.id
        if (userId == null) {
            AppResult.Failure(AppError.Unknown())
        } else {
            sessionManager.saveUserId(userId)
            AppResult.Success(userId)
        }
    } catch (e: RestException) {
        // Supabase trả cùng một lỗi cho email sai và mật khẩu sai, cố ý để không
        // ai dò được địa chỉ nào có tồn tại trong hệ thống.
        AppResult.Failure(
            when {
                e.message?.contains("email_not_confirmed", ignoreCase = true) == true ->
                    AppError.Business(
                        "EMAIL_NOT_CONFIRMED",
                        "Tài khoản chưa xác nhận email. Bật Auto Confirm User trong Supabase Dashboard.",
                    )

                else -> AppError.Business("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng")
            }
        )
    } catch (e: HttpRequestException) {
        AppResult.Failure(AppError.Network(e))
    } catch (e: Exception) {
        AppResult.Failure(AppError.Unknown(e))
    }

    override suspend fun signOut() {
        runCatching { auth.signOut() }
        // Xoá sạch DB: máy có thể được bàn giao cho nhân viên khác, không để lộ
        // khách hàng và giá của người trước.
        db.clearAllTables()
        sessionManager.clear()
    }

    override fun observeCurrentSalespersonId(): Flow<String?> =
        sessionManager.userId.flatMapLatest { userId ->
            if (userId == null) flowOf(null)
            else salespersonDao.observeByUserId(userId).map { it?.id }
        }
}
