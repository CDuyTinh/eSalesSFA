package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.SessionStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Ba đích đến khả dĩ khi mở app. */
enum class StartDestination { LOGIN, SYNC, HOME }

/**
 * Quyết định mở app vào đâu.
 *
 * Là quy tắc nghiệp vụ chứ không phải chuyện điều hướng: đã đăng nhập nhưng chưa
 * sync lần nào thì mọi màn hình đều trống trơn — UI chỉ đọc từ Room — nên phải
 * đi qua màn đồng bộ trước.
 */
class ResolveStartDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
) {

    suspend operator fun invoke(): StartDestination = when {
        !authRepository.isLoggedIn() -> StartDestination.LOGIN
        !sessionStore.hasCompletedFirstSync.first() -> StartDestination.SYNC
        else -> StartDestination.HOME
    }
}
