package com.tinhcd.esalessfa.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import com.tinhcd.esalessfa.domain.sync.SyncPhase
import com.tinhcd.esalessfa.domain.sync.SyncRun
import com.tinhcd.esalessfa.domain.sync.SyncRunStatus
import com.tinhcd.esalessfa.domain.sync.SyncTables
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Id của hai việc trong một lượt đồng bộ đầy đủ, dùng để theo dõi riêng lượt đó. */
private data class FullSyncHandle(val uploadId: UUID, val downloadId: UUID)

/**
 * Hiện thực [SyncScheduler] bằng WorkManager — điểm vào duy nhất để khởi động sync.
 *
 * Mọi nơi trong app đều gọi qua đây, không tự enqueue work: nhờ vậy chính sách
 * chống trùng và retry chỉ tồn tại ở một chỗ. Việc dịch WorkInfo sang [SyncRun]
 * cũng nằm ở đây, để androidx.work không rò lên tầng presentation.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext context: Context,
) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Tải master data.
     *
     * [ExistingWorkPolicy.KEEP] là mấu chốt chống sync trùng: bấm nút nhiều lần
     * hay app tự kích hoạt trong lúc đang chạy đều không tạo thêm lượt nào.
     * Đây chính là vai trò của `Lock.tryLock` trong bản Java cũ, nhưng bền hơn vì
     * WorkManager giữ trạng thái qua cả lần app bị kill.
     *
     * Truyền [force] = true khi user chủ động bấm đồng bộ và muốn xếp hàng chờ
     * lượt đang chạy xong.
     */
    override fun startDownload(force: Boolean) {
        workManager.enqueueUniqueWork(
            WORK_DOWNLOAD,
            if (force) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            downloadRequest(),
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun downloadRequest() = OneTimeWorkRequestBuilder<SyncDownloadWorker>()
        .setConstraints(networkConstraints())
        // Mất sóng giữa đồng là chuyện thường ngoài thị trường. Backoff giãn
        // dần thay vì đập liên tục vào server và ngốn pin.
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    private fun uploadRequest() = OneTimeWorkRequestBuilder<SyncUploadWorker>()
        .setConstraints(networkConstraints())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    /**
     * Đẩy outbox lên server.
     *
     * Gọi sau mỗi lần chốt đơn: có mạng thì đơn lên ngay, không mạng thì
     * WorkManager giữ lại và tự chạy khi kết nối trở lại.
     */
    override fun startUpload() {
        workManager.enqueueUniqueWork(
            WORK_UPLOAD,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadRequest(),
        )
    }

    /**
     * Đồng bộ đầy đủ: GỬI LÊN TRƯỚC rồi mới tải xuống.
     *
     * Thứ tự này quan trọng. Tải xuống trước có thể ghi đè master data mà đơn
     * hàng đang chờ gửi tham chiếu tới (giá, chương trình khuyến mãi), khiến
     * server đối chiếu giá và từ chối đơn vốn hoàn toàn hợp lệ lúc tạo.
     *
     * APPEND_OR_REPLACE thay vì KEEP vì lượt này do user chủ động bấm: nó phải
     * chạy chứ không được bỏ qua. Đang có lượt khác thì xếp sau, vẫn không có
     * hai lượt chạy song song.
     *
     * Luồng trả về gắn với id của hai việc vừa xếp nên chỉ báo tiến trình của
     * ĐÚNG lượt này. Quan sát theo tên unique thì sẽ thấy luôn cả kết quả
     * SUCCEEDED của lượt trước còn lưu lại, và màn hình báo "xong" ngay khi mở.
     */
    override fun startFullSync(): Flow<SyncRun> {
        val upload = uploadRequest()
        val download = downloadRequest()
        workManager
            .beginUniqueWork(WORK_FULL, ExistingWorkPolicy.APPEND_OR_REPLACE, upload)
            .then(download)
            .enqueue()

        val handle = FullSyncHandle(uploadId = upload.id, downloadId = download.id)
        return workManager
            .getWorkInfosFlow(WorkQuery.fromIds(listOf(handle.uploadId, handle.downloadId)))
            .map { infos -> infos.toSyncRun(handle) }
    }

    /** Trạng thái lượt tải xuống hiện tại để UI hiển thị tiến trình. */
    override fun observeDownload(): Flow<SyncRun> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_DOWNLOAD)
            .map { list -> list.lastOrNull().toSyncRun() }

    private fun WorkInfo?.toSyncRun(): SyncRun {
        if (this == null) return SyncRun()

        val snapshot = SyncDownloadWorker.progressOf(progress)

        return when (state) {
            WorkInfo.State.RUNNING -> SyncRun(
                status = SyncRunStatus.RUNNING,
                page = snapshot.page,
                totalRows = snapshot.totalRows,
                currentTable = snapshot.currentTable,
                doneTables = snapshot.doneTables,
            )

            WorkInfo.State.SUCCEEDED -> SyncRun(
                status = SyncRunStatus.SUCCEEDED,
                page = outputData.getInt(SyncDownloadWorker.KEY_PAGE, snapshot.page),
                totalRows = outputData.getInt(
                    SyncDownloadWorker.KEY_TOTAL_ROWS,
                    snapshot.totalRows,
                ),
                doneTables = SyncTables.ALL.toSet(),
            )

            WorkInfo.State.FAILED -> SyncRun(
                status = SyncRunStatus.FAILED,
                errorMessage = outputData.getString(SyncDownloadWorker.KEY_ERROR),
            )

            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                SyncRun(status = SyncRunStatus.RUNNING)

            WorkInfo.State.CANCELLED -> SyncRun(status = SyncRunStatus.CANCELLED)
        }
    }

    /**
     * Gộp trạng thái của cả chuỗi gửi-lên-rồi-tải-xuống.
     *
     * Danh sách chỉ chứa hai việc của đúng lượt này, nhưng WorkManager không hứa
     * thứ tự nên phải tìm việc tải xuống theo id thay vì lấy phần tử cuối.
     */
    private fun List<WorkInfo>.toSyncRun(handle: FullSyncHandle): SyncRun {
        // Chưa thấy việc nào tức là WorkManager chưa ghi xong — vẫn coi là đang
        // chạy để màn hình không nhá qua trạng thái rỗng.
        if (isEmpty()) return SyncRun(status = SyncRunStatus.RUNNING)

        firstOrNull { it.state == WorkInfo.State.FAILED }?.let { failed ->
            return SyncRun(
                status = SyncRunStatus.FAILED,
                errorMessage = failed.outputData.getString(SyncDownloadWorker.KEY_ERROR),
            )
        }
        if (any { it.state == WorkInfo.State.CANCELLED }) {
            return SyncRun(status = SyncRunStatus.CANCELLED)
        }

        val download = firstOrNull { it.id == handle.downloadId }

        // Chỉ báo xong khi CẢ HAI việc đã xong. Việc tải xuống chạy sau việc gửi
        // lên, nên xét từng việc riêng sẽ báo xong quá sớm.
        if (size == 2 && all { it.state == WorkInfo.State.SUCCEEDED }) {
            return SyncRun(
                status = SyncRunStatus.SUCCEEDED,
                page = download?.outputData?.getInt(SyncDownloadWorker.KEY_PAGE, 0) ?: 0,
                totalRows = download?.outputData?.getInt(SyncDownloadWorker.KEY_TOTAL_ROWS, 0) ?: 0,
                doneTables = SyncTables.ALL.toSet(),
            )
        }

        // Số trang/số dòng chỉ có ý nghĩa ở bước tải xuống; bước gửi lên chạy
        // nhanh nên để trống là đủ.
        val snapshot = SyncDownloadWorker.progressOf(download?.progress ?: Data.EMPTY)

        // Việc tải xuống còn BLOCKED nghĩa là việc gửi lên chưa xong — màn hình
        // nói rõ đang ở bước nào thay vì để người dùng nhìn thanh 0% đứng yên.
        val phase =
            if (download?.state == WorkInfo.State.RUNNING) SyncPhase.DOWNLOAD else SyncPhase.UPLOAD

        return SyncRun(
            status = SyncRunStatus.RUNNING,
            phase = phase,
            page = snapshot.page,
            totalRows = snapshot.totalRows,
            currentTable = snapshot.currentTable,
            doneTables = snapshot.doneTables,
        )
    }

    private companion object {
        const val WORK_DOWNLOAD = "sync_download"
        const val WORK_UPLOAD = "sync_upload"
        const val WORK_FULL = "sync_full"
    }
}
