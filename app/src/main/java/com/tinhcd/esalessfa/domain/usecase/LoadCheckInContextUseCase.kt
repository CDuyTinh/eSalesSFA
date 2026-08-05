package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.visit.CheckInConfig
import com.tinhcd.esalessfa.domain.model.visit.ReasonCode
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import javax.inject.Inject

/** Mọi thứ màn check-in cần trước khi bắt đầu đo vị trí. */
data class CheckInContext(
    val customer: Customer?,
    val config: CheckInConfig,
    val reasonCodes: List<ReasonCode>,
)

/**
 * Nạp bối cảnh cho màn check-in.
 *
 * Ba thứ này luôn đi cùng nhau và phụ thuộc lẫn nhau: ngưỡng bán kính lấy từ
 * app_configs, còn danh sách lý do phải đúng nhóm dành cho tình huống vượt bán
 * kính — nếu lấy nhầm nhóm thì màn hình vẫn chạy nhưng dữ liệu ghi lên server
 * sai loại. Việc ghép đúng cặp đó là quy tắc nghiệp vụ, không phải chuyện của
 * giao diện.
 */
class LoadCheckInContextUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val visitRepository: VisitRepository,
) {

    suspend operator fun invoke(customerId: String): CheckInContext = CheckInContext(
        customer = customerRepository.getById(customerId),
        config = visitRepository.checkInConfig(),
        reasonCodes = visitRepository.reasonCodes(VisitRepository.REASON_OVER_DISTANCE),
    )
}
