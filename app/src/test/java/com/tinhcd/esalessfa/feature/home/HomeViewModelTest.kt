package com.tinhcd.esalessfa.feature.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.common.AppResult
import com.tinhcd.esalessfa.domain.repository.AuthRepository
import com.tinhcd.esalessfa.domain.repository.SessionStore
import com.tinhcd.esalessfa.domain.usecase.ResolveStartDestinationUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `da sync hom nay thi khong nhac gi`() = runTest {
        val viewModel = viewModel(lastSync = LocalDate.now())

        viewModel.events.test {
            viewModel.onResumed()
            expectNoEvents()
        }
    }

    /** App nằm mở xuyên qua nửa đêm: Splash không chạy nên Home phải bắt. */
    @Test
    fun `sync gan nhat la hom qua thi nhac dong bo`() = runTest {
        val viewModel = viewModel(lastSync = LocalDate.now().minusDays(1))

        viewModel.events.test {
            viewModel.onResumed()
            assertThat(awaitItem()).isEqualTo(HomeEvent.SyncNewDay)
        }
    }

    /** Bấm Back thoát màn đồng bộ mà bị đẩy vào lại thì không còn đường ra. */
    @Test
    fun `chi nhac mot lan du onResume chay lai`() = runTest {
        val viewModel = viewModel(lastSync = LocalDate.now().minusDays(1))

        viewModel.events.test {
            viewModel.onResumed()
            assertThat(awaitItem()).isEqualTo(HomeEvent.SyncNewDay)

            viewModel.onResumed()
            viewModel.onResumed()
            expectNoEvents()
        }
    }

    private fun viewModel(lastSync: LocalDate?) = HomeViewModel(
        ResolveStartDestinationUseCase(
            authRepository = FakeAuthRepository,
            sessionStore = FakeSessionStore(lastSync),
        )
    )
}

private object FakeAuthRepository : AuthRepository {

    override suspend fun isLoggedIn(): Boolean = true

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
