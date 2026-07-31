package com.tinhcd.esalessfa.domain.repository

/**
 * Cổng khởi động hàng đợi upload ảnh.
 *
 * Tách khỏi [SyncScheduler] vì ràng buộc khác nhau: ảnh nặng nên chỉ upload khi
 * không ở chế độ tiết kiệm pin, còn dữ liệu giao dịch thì phải lên bằng mọi giá.
 */
interface PhotoUploader {

    /** Xin chạy hàng đợi. Gọi nhiều lần liên tiếp không tạo thêm lượt nào. */
    fun start()
}
