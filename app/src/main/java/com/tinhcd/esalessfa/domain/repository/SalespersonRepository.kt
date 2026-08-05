package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.customer.Salesperson
import kotlinx.coroutines.flow.Flow

/** Hồ sơ nhân viên đang đăng nhập, lấy từ Room sau khi sync. */
interface SalespersonRepository {
    fun observeCurrent(): Flow<Salesperson?>
}
