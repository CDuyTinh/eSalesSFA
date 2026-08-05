package com.tinhcd.esalessfa.data.repository

import android.content.Context
import com.tinhcd.esalessfa.core.common.dispatcher.DispatcherProvider
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.dao.AppConfigDao
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.ReasonCodeDao
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.core.database.entity.local.PendingUploadEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.VisitEntity
import com.tinhcd.esalessfa.core.media.ImageProcessor
import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.visit.ActiveVisit
import com.tinhcd.esalessfa.domain.model.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.model.visit.CheckInPhoto
import com.tinhcd.esalessfa.domain.model.visit.CheckInResult
import com.tinhcd.esalessfa.domain.model.visit.CheckInValidator
import com.tinhcd.esalessfa.domain.model.visit.LocationSample
import com.tinhcd.esalessfa.domain.model.visit.OpenVisit
import com.tinhcd.esalessfa.domain.model.visit.ReasonCode
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class VisitRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val visitDao: VisitDao,
    private val appConfigDao: AppConfigDao,
    private val reasonCodeDao: ReasonCodeDao,
    private val salespersonDao: SalespersonDao,
    private val pendingUploadDao: PendingUploadDao,
    private val imageProcessor: ImageProcessor,
    private val dispatchers: DispatcherProvider,
) : VisitRepository {

    /**
     * Đọc ngưỡng từ app_configs; thiếu cấu hình thì dùng mặc định an toàn của
     * [CheckInConfig] thay vì crash — server chưa seed đủ không được làm nhân
     * viên đứng hình giữa cửa hàng.
     */
    override suspend fun checkInConfig(): CheckInConfig {
        val defaults = CheckInConfig()
        return CheckInConfig(
            radiusMeters = config("CHECKIN_RADIUS_M")?.toDoubleOrNull() ?: defaults.radiusMeters,
            maxAccuracyMeters = config("GPS_MAX_ACCURACY_M")?.toFloatOrNull()
                ?: defaults.maxAccuracyMeters,
            validateType = config("CHECKIN_VALIDATE_TYPE")?.toIntOrNull() ?: defaults.validateType,
            minVisitMinutes = config("MIN_VISIT_MINUTES")?.toIntOrNull() ?: defaults.minVisitMinutes,
        )
    }

    private suspend fun config(code: String): String? = appConfigDao.getValue(code)

    override suspend fun reasonCodes(applyFor: String): List<ReasonCode> =
        reasonCodeDao.getByApplyFor(applyFor).map { ReasonCode(it.code, it.name) }

    override suspend fun openVisit(customerId: String): OpenVisit? =
        visitDao.getOpenVisit(customerId, today())?.let {
            OpenVisit(it.id, it.customerId, it.checkInAt)
        }

    override fun observeOpenVisit(customerId: String): Flow<OpenVisit?> =
        visitDao.observeOpenVisit(customerId, today()).map { visit ->
            visit?.let { OpenVisit(it.id, it.customerId, it.checkInAt) }
        }

    override suspend fun hasOpenVisit(): Boolean = visitDao.countOpenVisits() > 0

    override fun observeActiveVisit(): Flow<ActiveVisit?> =
        visitDao.observeActiveVisit().map { row ->
            row?.let { ActiveVisit(it.visitId, it.customerId, it.customerName, it.checkInAt) }
        }

    override suspend fun prepareCheckInPhoto(
        rawPath: String,
        location: GeoPoint?,
        customerName: String,
    ): CheckInPhoto {
        val raw = File(rawPath)
        val target = File(File(context.cacheDir, PHOTO_DIR), "${UUID.randomUUID()}.jpg")
        val processed = imageProcessor.process(raw, target, location, customerName)
        raw.delete()

        return CheckInPhoto(processed.file.absolutePath, processed.sizeBytes)
    }

    override suspend fun discardCheckInPhoto(photo: CheckInPhoto) {
        withContext(dispatchers.io) { File(photo.path).delete() }
    }

    override suspend fun checkIn(
        customerId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        reasonCode: String?,
        note: String?,
        photo: CheckInPhoto?,
        batteryPct: Int?,
    ): CheckInResult {
        val salesperson = requireNotNull(salespersonDao.getCurrent()) {
            "Chưa có hồ sơ nhân viên — cần đồng bộ trước khi check-in"
        }

        // Kiểm tra ngay trước khi ghi, tại tầng dữ liệu.
        //
        // Chặn ở ViewModel không đủ: state ở đó có thể còn cũ khi user bấm nhanh,
        // và mọi đường gọi khác trong tương lai sẽ phải tự nhớ kiểm tra lại. Chốt
        // ở đây thì không có cách nào tạo được hai lượt ghé mở cùng lúc.
        //
        // Chặn cả trường hợp CÙNG cửa hàng: ghé lại nơi đang mở sẽ tạo lượt thứ
        // hai và không lượt nào có giờ ra đúng.
        visitDao.getActiveVisit()?.let { row ->
            return CheckInResult.AlreadyOpen(
                visit = ActiveVisit(row.visitId, row.customerId, row.customerName, row.checkInAt),
                isSameCustomer = row.customerId == customerId,
            )
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        visitDao.upsert(
            VisitEntity(
                id = id,
                customerId = customerId,
                salespersonId = salesperson.id,
                visitDate = today(),
                isInRoute = true,
                checkInAt = now,
                checkInLat = sample?.point?.latitude,
                checkInLng = sample?.point?.longitude,
                checkInAccuracy = sample?.accuracy,
                checkInDistance = distanceMeters?.toFloat(),
                checkOutAt = null,
                checkOutLat = null,
                checkOutLng = null,
                checkOutDistance = null,
                durationMinutes = null,
                reasonCode = reasonCode,
                note = note,
                isMockLocation = sample?.isMock ?: false,
                batteryPct = batteryPct,
                deviceId = null,
                checkInPhotoPath = photo?.path,
                // DRAFT cho tới khi check-out: đẩy lượt ghé chưa kết thúc lên
                // server sẽ tạo bản ghi thiếu giờ ra và không bao giờ được sửa lại.
                syncStatus = SyncStatus.DRAFT,
                clientCreatedAt = now,
            )
        )

        // Ảnh xếp hàng đi ngay, không đợi check-out như bản ghi lượt ghé: file
        // nằm trong cache nên để càng lâu càng dễ bị hệ thống dọn mất.
        if (photo != null) {
            pendingUploadDao.upsert(
                PendingUploadEntity(
                    id = UUID.randomUUID().toString(),
                    entityType = ENTITY_VISIT_PHOTO,
                    entityId = id,
                    localPath = photo.path,
                    bucket = PhotoUploadRepositoryImpl.BUCKET_VISIT_PHOTOS,
                    remotePath = "${salesperson.id}/$id.jpg",
                    fileSize = photo.sizeBytes.toLong(),
                    status = PhotoUploadRepositoryImpl.STATUS_PENDING,
                    createdAt = now,
                )
            )
        }

        return CheckInResult.Success(id)
    }

    override suspend fun checkOut(
        visitId: String,
        sample: LocationSample?,
        distanceMeters: Double?,
        note: String?,
    ) {
        val visit = visitDao.getById(visitId) ?: return
        val now = System.currentTimeMillis()

        visitDao.upsert(
            visit.copy(
                checkOutAt = now,
                checkOutLat = sample?.point?.latitude,
                checkOutLng = sample?.point?.longitude,
                checkOutDistance = distanceMeters?.toFloat(),
                durationMinutes = CheckInValidator.visitDurationMinutes(visit.checkInAt, now),
                // Không có ghi chú mới thì giữ ghi chú đã nhập lúc check-in, chứ
                // không ghi đè bằng null.
                note = note?.takeIf { it.isNotBlank() } ?: visit.note,
                // Lượt ghé đã trọn vẹn -> vào outbox chờ đẩy lên.
                syncStatus = SyncStatus.PENDING,
            )
        )
    }

    override fun observeTodayVisitCount(): Flow<Int> =
        visitDao.observeByDate(today()).map { it.size }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        /** Khớp với nhánh xử lý sau khi upload xong trong PhotoUploadRepositoryImpl. */
        const val ENTITY_VISIT_PHOTO = "VISIT_PHOTO"

        private const val PHOTO_DIR = "visit-photos"
    }
}
