package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.stock.StockCountLine

interface StockCountRepository {

    /** SKU cần kiểm kê, lấy từ lịch sử mua của chính khách hàng đó. */
    suspend fun linesFor(customerId: String): List<StockCountLine>

    /**
     * Lưu phiếu kiểm kê. Chỉ ghi những dòng ĐÃ NHẬP — dòng bỏ trống nghĩa là
     * nhân viên chưa đếm, khác hẳn với đếm ra 0.
     */
    suspend fun save(customerId: String, lines: List<StockCountLine>, note: String?): String
}
