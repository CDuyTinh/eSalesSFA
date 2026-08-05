package com.tinhcd.esalessfa.feature.sync

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.model.sync.SyncRun
import com.tinhcd.esalessfa.domain.model.sync.SyncRunStatus
import com.tinhcd.esalessfa.domain.repository.SessionStore
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
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

/**
 * Test được vì SyncViewModel nói chuyện qua [SyncScheduler] chứ không qua
 * WorkManager: bản giả bên dưới phát ra [SyncRun] tuỳ ý, không cần thiết bị.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope chạy trên Dispatchers.Main; trong test JVM phải thay.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `FIRST_RUN bam start thi goi tai xuong va soi tien trinh`() = runTest {
        val scheduler = FakeSyncScheduler()
        val viewModel = viewModel(scheduler, SyncMode.FIRST_RUN)

        viewModel.start()
        assertThat(scheduler.downloadStarts).isEqualTo(1)

        viewModel.uiState.test {
            scheduler.download.value = SyncRun(
                status = SyncRunStatus.RUNNING,
                page = 2,
                totalRows = 2013,
                currentTable = "customers",
            )
            val state = expectMostRecentItem()
            assertThat(state.isRunning).isTrue()
            assertThat(state.page).isEqualTo(2)
            assertThat(state.totalRows).isEqualTo(2013)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loi khong co loi nhan tu server thi hien cau mac dinh`() = runTest {
        val scheduler = FakeSyncScheduler()
        val viewModel = viewModel(scheduler, SyncMode.FIRST_RUN)

        viewModel.uiState.test {
            scheduler.download.value = SyncRun(status = SyncRunStatus.FAILED)
            assertThat(expectMostRecentItem().errorMessage).isEqualTo("Đồng bộ thất bại")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Fragment gọi start() lại mỗi lần view dựng lại — không được xếp thêm lượt. */
    @Test
    fun `MANUAL goi start nhieu lan chi xep mot luot`() = runTest {
        val scheduler = FakeSyncScheduler()
        val viewModel = viewModel(scheduler, SyncMode.MANUAL)

        viewModel.start()
        viewModel.start()
        viewModel.start()

        assertThat(scheduler.fullSyncStarts).isEqualTo(1)
    }

    @Test
    fun `MANUAL bam thu lai thi xep luot moi`() = runTest {
        val scheduler = FakeSyncScheduler()
        val viewModel = viewModel(scheduler, SyncMode.MANUAL)

        viewModel.start()
        viewModel.retry()

        assertThat(scheduler.fullSyncStarts).isEqualTo(2)
    }

    /** Chỉ lượt đầu tiên mới được đánh dấu "đã sync lần đầu". */
    @Test
    fun `xong luot FIRST_RUN thi danh dau da sync lan dau`() = runTest {
        val scheduler = FakeSyncScheduler()
        val session = FakeSessionStore()
        val viewModel = viewModel(scheduler, SyncMode.FIRST_RUN, session)

        viewModel.onSyncCompleted()

        assertThat(session.firstSyncMarked).isTrue()
        assertThat(viewModel.finished.value).isTrue()
    }

    @Test
    fun `xong luot MANUAL thi khong danh dau lai`() = runTest {
        val scheduler = FakeSyncScheduler()
        val session = FakeSessionStore()
        val viewModel = viewModel(scheduler, SyncMode.MANUAL, session)

        viewModel.onSyncCompleted()

        assertThat(session.firstSyncMarked).isFalse()
        assertThat(viewModel.finished.value).isTrue()
    }

    private fun viewModel(
        scheduler: SyncScheduler,
        mode: SyncMode,
        session: SessionStore = FakeSessionStore(),
    ) = SyncViewModel(
        syncScheduler = scheduler,
        sessionStore = session,
        savedStateHandle = SavedStateHandle(mapOf(SyncViewModel.ARG_MODE to mode.name)),
    )
}

private class FakeSyncScheduler : SyncScheduler {

    val download = MutableStateFlow(SyncRun())
    var downloadStarts = 0
        private set
    var fullSyncStarts = 0
        private set

    override fun startDownload(force: Boolean) {
        downloadStarts++
    }

    override fun startUpload() = Unit

    override fun startFullSync(): Flow<SyncRun> {
        fullSyncStarts++
        return flowOf(SyncRun(status = SyncRunStatus.RUNNING))
    }

    override fun observeDownload(): Flow<SyncRun> = download
}

private class FakeSessionStore : SessionStore {

    var firstSyncMarked = false
        private set

    override val userId = MutableStateFlow<String?>(null)

    override val hasCompletedFirstSync = MutableStateFlow(false)

    override suspend fun saveUserId(id: String) = Unit

    override suspend fun markFirstSyncCompleted() {
        firstSyncMarked = true
    }

    override suspend fun clear() = Unit
}
