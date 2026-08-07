package com.tinhcd.esalessfa.feature.sync

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.model.sync.SyncRun
import com.tinhcd.esalessfa.domain.model.sync.SyncRunStatus
import com.tinhcd.esalessfa.domain.repository.SessionStore
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
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

    /** Ngày mới phải gửi outbox tồn của hôm qua trước khi tải, không chỉ tải xuống. */
    @Test
    fun `DAILY bam start thi chay chuoi day du`() = runTest {
        val scheduler = FakeSyncScheduler()
        val viewModel = viewModel(scheduler, SyncMode.DAILY)

        viewModel.start()
        viewModel.start()

        assertThat(scheduler.fullSyncStarts).isEqualTo(1)
        assertThat(scheduler.downloadStarts).isEqualTo(0)
    }

    @Test
    fun `xong luot FIRST_RUN thi ghi mo ngay sync hom nay`() = runTest {
        val scheduler = FakeSyncScheduler()
        val session = FakeSessionStore()
        val viewModel = viewModel(scheduler, SyncMode.FIRST_RUN, session)

        viewModel.onSyncCompleted()

        assertThat(session.lastSyncDate.value).isEqualTo(LocalDate.now())
        assertThat(viewModel.finished.value).isTrue()
    }

    /** Đã tự bấm đồng bộ trong ngày thì mở lại app không bị chặn ở màn này nữa. */
    @Test
    fun `xong luot MANUAL cung ghi mo ngay sync`() = runTest {
        val scheduler = FakeSyncScheduler()
        val session = FakeSessionStore()
        val viewModel = viewModel(scheduler, SyncMode.MANUAL, session)

        viewModel.onSyncCompleted()

        assertThat(session.lastSyncDate.value).isEqualTo(LocalDate.now())
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

    override val userId = MutableStateFlow<String?>(null)

    override val lastSyncDate = MutableStateFlow<LocalDate?>(null)

    override suspend fun saveUserId(id: String) = Unit

    override suspend fun markSyncCompleted(date: LocalDate) {
        lastSyncDate.value = date
    }

    override suspend fun clear() = Unit
}
