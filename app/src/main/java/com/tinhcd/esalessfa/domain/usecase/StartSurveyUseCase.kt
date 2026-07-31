package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.survey.SurveyQuestion
import javax.inject.Inject

/** Bài khảo sát vừa mở: bản nháp đã tạo, bộ câu hỏi và ngưỡng đạt. */
data class SurveyDraft(
    val surveyId: String,
    val customer: Customer?,
    val questions: List<SurveyQuestion>,
    val passScore: Double,
)

/**
 * Mở bài khảo sát cho một cửa hàng.
 *
 * Bốn việc phải làm đúng thứ tự: lấy khách, lấy bộ câu hỏi của loại khảo sát,
 * lấy ngưỡng đạt của chính loại đó, rồi mới tạo bản nháp. Lấy ngưỡng của loại
 * khác thì màn hình vẫn chạy nhưng chấm "đạt/không đạt" sai — nên đây là quy
 * tắc nghiệp vụ chứ không phải chuỗi lệnh khởi tạo màn hình.
 */
class StartSurveyUseCase @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val customerRepository: CustomerRepository,
) {

    suspend operator fun invoke(surveyTypeId: String, customerId: String): SurveyDraft {
        val customer = customerRepository.getById(customerId)
        val questions = surveyRepository.questions(surveyTypeId)
        val passScore = surveyRepository.types()
            .firstOrNull { it.id == surveyTypeId }?.passScore ?: 0.0

        return SurveyDraft(
            surveyId = surveyRepository.startDraft(surveyTypeId, customerId),
            customer = customer,
            questions = questions,
            passScore = passScore,
        )
    }
}
