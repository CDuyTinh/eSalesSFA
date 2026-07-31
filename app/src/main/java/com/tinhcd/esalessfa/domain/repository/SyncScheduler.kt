package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.sync.SyncRun
import kotlinx.coroutines.flow.Flow

/**
 * Cổng khởi động và theo dõi đồng bộ.
 *
 * Khai ở domain chứ không để ViewModel gọi thẳng lớp bọc WorkManager: nhờ vậy
 * ViewModel test được bằng một bản giả trả [SyncRun] tuỳ ý, không cần WorkManager
 * thật lẫn Robolectric.
 *
 * Interface cố ý KHÔNG nhắc tới WorkInfo, Data hay UUID — đó là chi tiết của
 * hiện thực, và chính chúng là thứ trước đây kéo androidx.work vào tầng
 * presentation.
 */
interface SyncScheduler {

    /**
     * Tải master data.
     *
     * @param force true khi user chủ động bấm đồng bộ và muốn xếp hàng chờ lượt
     *   đang chạy xong; false thì đang có lượt chạy là bỏ qua.
     */
    fun startDownload(force: Boolean = false)

    /** Đẩy outbox lên server. Gọi sau mỗi lần chốt đơn, kiểm kê, khảo sát. */
    fun startUpload()

    /**
     * Xếp một lượt đồng bộ đầy đủ (gửi lên rồi tải xuống) và trả về luồng trạng
     * thái CỦA ĐÚNG lượt vừa xếp.
     *
     * Trả Flow ngay tại đây thay vì đưa ra một mã lượt rồi bắt gọi thêm hàm quan
     * sát: mã lượt là chi tiết của hiện thực (WorkManager cần hai UUID), và nếu
     * quan sát theo tên công việc thì sẽ thấy cả kết quả thành công của lượt
     * trước còn lưu lại, làm màn hình báo "xong" ngay khi vừa mở.
     */
    fun startFullSync(): Flow<SyncRun>

    /** Trạng thái lượt tải xuống hiện tại. */
    fun observeDownload(): Flow<SyncRun>
}
