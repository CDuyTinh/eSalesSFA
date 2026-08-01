package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.repository.ActiveVisit
import com.tinhcd.esalessfa.domain.repository.CheckInPhoto
import com.tinhcd.esalessfa.domain.repository.OpenVisit
import com.tinhcd.esalessfa.domain.repository.ReasonCode
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.visit.LocationSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Quy tắc thời gian ghé tối thiểu, test không cần Android.
 *
 * Trước khi tách use case, phép trừ "còn mấy phút nữa" nằm trong ViewModel nên
 * chỉ có thể kiểm bằng cách chạy app thật.
 */
class CheckOutUseCaseTest {

    private val config = CheckInConfig(minVisitMinutes = 5)
    private val checkInAt = 1_700_000_000_000L

    private val visit = OpenVisit(
        id = "visit-1",
        customerId = "KH001",
        checkInAt = checkInAt,
    )

    @Test
    fun `chua du thoi gian toi thieu thi tra ve TooEarly va khong ghi gi`() = runTest {
        val repository = RecordingVisitRepository()
        val useCase = CheckOutUseCase(repository)

        // Mới ghé được 2 phút.
        val result = useCase(
            visit = visit,
            config = config,
            sample = null,
            distanceMeters = null,
            note = null,
            now = checkInAt + 2 * 60_000L,
        )

        assertThat(result).isEqualTo(CheckOutResult.TooEarly(minutesLeft = 3))
        assertThat(repository.checkedOutVisitId).isNull()
    }

    @Test
    fun `du thoi gian thi check-out va bao Done`() = runTest {
        val repository = RecordingVisitRepository()
        val useCase = CheckOutUseCase(repository)

        val result = useCase(
            visit = visit,
            config = config,
            sample = null,
            distanceMeters = 12.5,
            note = "xong",
            now = checkInAt + 6 * 60_000L,
        )

        assertThat(result).isEqualTo(CheckOutResult.Done)
        assertThat(repository.checkedOutVisitId).isEqualTo("visit-1")
    }

    /** Vừa đúng ngưỡng cũng phải cho ra: chặn ở đây là chặn nhầm. */
    @Test
    fun `dung bang nguong toi thieu thi cho check-out`() = runTest {
        val repository = RecordingVisitRepository()
        val useCase = CheckOutUseCase(repository)

        val result = useCase(
            visit = visit,
            config = config,
            sample = null,
            distanceMeters = null,
            note = null,
            now = checkInAt + 5 * 60_000L,
        )

        assertThat(result).isEqualTo(CheckOutResult.Done)
    }
}

/** Bản giả chỉ ghi lại lời gọi — đủ để kiểm quy tắc, không cần Room. */
private class RecordingVisitRepository : VisitRepository {

    var checkedOutVisitId: String? = null
        private set

    override suspend fun checkOut(
        visitId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        note: String?,
    ) {
        checkedOutVisitId = visitId
    }

    override suspend fun checkInConfig(): CheckInConfig = CheckInConfig()

    override suspend fun reasonCodes(applyFor: String): List<ReasonCode> = emptyList()

    override suspend fun openVisit(customerId: String): OpenVisit? = null

    override fun observeOpenVisit(customerId: String): Flow<OpenVisit?> = flowOf(null)

    override fun observeActiveVisit(): Flow<ActiveVisit?> = flowOf(null)

    override suspend fun hasOpenVisit(): Boolean = false

    override suspend fun prepareCheckInPhoto(
        rawPath: String,
        location: GeoPoint?,
        customerName: String,
    ): CheckInPhoto = CheckInPhoto(rawPath, 0)

    override suspend fun discardCheckInPhoto(photo: CheckInPhoto) = Unit

    override suspend fun checkIn(
        customerId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        reasonCode: String?,
        note: String?,
        photo: CheckInPhoto?,
        batteryPct: Int?,
    ): VisitRepository.CheckInResult = VisitRepository.CheckInResult.Success("visit-1")

    override fun observeTodayVisitCount(): Flow<Int> = flowOf(0)
}
