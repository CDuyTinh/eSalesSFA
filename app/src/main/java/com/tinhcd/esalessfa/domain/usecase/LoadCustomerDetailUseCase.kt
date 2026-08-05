package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.survey.SurveyTypeInfo
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import javax.inject.Inject

/** Hồ sơ cửa hàng kèm những loại khảo sát nhân viên được phép làm tại đây. */
data class CustomerDetail(
    val customer: Customer?,
    val surveyTypes: List<SurveyTypeInfo>,
)

/**
 * Nạp màn chi tiết khách hàng: hồ sơ và danh sách khảo sát khả dụng.
 *
 * Hai nguồn khác nhau nhưng luôn hiện cùng một lúc, nên gom lại để màn hình
 * không phải tự biết cần hỏi những kho nào.
 */
class LoadCustomerDetailUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val surveyRepository: SurveyRepository,
) {

    suspend operator fun invoke(customerId: String): CustomerDetail = CustomerDetail(
        customer = customerRepository.getById(customerId),
        surveyTypes = surveyRepository.types(),
    )
}
