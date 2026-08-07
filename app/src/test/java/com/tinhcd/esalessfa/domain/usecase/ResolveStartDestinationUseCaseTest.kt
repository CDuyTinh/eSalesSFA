package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.common.AppResult
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.SessionStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResolveStartDestinationUseCaseTest {

    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun `chua dang nhap thi vao Login`() = runTest {
        val useCase = useCase(loggedIn = false, lastSync = today)

        assertThat(useCase(today)).isEqualTo(StartDestination.LOGIN)
    }

    @Test
    fun `chua sync lan nao thi vao man dong bo lan dau`() = runTest {
        val useCase = useCase(loggedIn = true, lastSync = null)

        assertThat(useCase(today)).isEqualTo(StartDestination.FIRST_SYNC)
    }

    /** Đúng trường hợp cần thêm: mở app buổi sáng, dữ liệu còn là của hôm qua. */
    @Test
    fun `sync gan nhat la hom qua thi vao man dong bo dau ngay`() = runTest {
        val useCase = useCase(loggedIn = true, lastSync = today.minusDays(1))

        assertThat(useCase(today)).isEqualTo(StartDestination.DAILY_SYNC)
    }

    @Test
    fun `da sync trong ngay thi vao thang Home`() = runTest {
        val useCase = useCase(loggedIn = true, lastSync = today)

        assertThat(useCase(today)).isEqualTo(StartDestination.HOME)
    }

    /** Đồng hồ máy bị chỉnh lùi: không bắt sync lại, giống bản Java cũ. */
    @Test
    fun `moc sync o tuong lai thi van vao Home`() = runTest {
        val useCase = useCase(loggedIn = true, lastSync = today.plusDays(1))

        assertThat(useCase(today)).isEqualTo(StartDestination.HOME)
    }

    private fun useCase(loggedIn: Boolean, lastSync: LocalDate?) =
        ResolveStartDestinationUseCase(
            authRepository = FakeAuthRepository(loggedIn),
            sessionStore = FakeSessionStore(lastSync),
        )
}

private class FakeAuthRepository(private val loggedIn: Boolean) : AuthRepository {

    override suspend fun isLoggedIn(): Boolean = loggedIn

    override suspend fun signIn(email: String, password: String): AppResult<String> =
        AppResult.Success("")

    override suspend fun signOut() = Unit

    override fun observeCurrentSalespersonId(): Flow<String?> = flowOf(null)
}

private class FakeSessionStore(lastSync: LocalDate?) : SessionStore {

    override val userId = MutableStateFlow<String?>(null)

    override val lastSyncDate = MutableStateFlow(lastSync)

    override suspend fun saveUserId(id: String) = Unit

    override suspend fun markSyncCompleted(date: LocalDate) {
        lastSyncDate.value = date
    }

    override suspend fun clear() = Unit
}
