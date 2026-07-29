package com.tinhcd.esalessfa.core.database

/**
 * Vòng đời đồng bộ của một bản ghi giao dịch.
 *
 * Chỉ đặt trên bảng gốc (visits, orders, ...). Bảng con đi theo cha, không mang
 * trạng thái riêng — nếu không sẽ xuất hiện tình trạng đơn đã SYNCED mà vài dòng
 * chi tiết còn PENDING.
 */
enum class SyncStatus {
    /** Nháp, user chưa xác nhận. KHÔNG đẩy lên server. */
    DRAFT,

    /** Đã xác nhận, đang chờ upload. Đây chính là "outbox". */
    PENDING,

    /** Worker đang gửi. Tránh hai worker cùng gửi một bản ghi. */
    SYNCING,

    /** Server đã ghi nhận. */
    SYNCED,

    /**
     * Server từ chối vì lỗi nghiệp vụ (giá lệch, khách ngoài tuyến...).
     * KHÔNG retry tự động — phải cho user thấy và xử lý.
     */
    FAILED,
}
