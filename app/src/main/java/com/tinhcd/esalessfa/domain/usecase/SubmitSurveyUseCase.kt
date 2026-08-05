package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.model.survey.SurveyIssue
import com.tinhcd.esalessfa.domain.model.survey.SurveyQuestion
import com.tinhcd.esalessfa.domain.model.survey.SurveyScorer
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.repository.SyncScheduler
import javax.inject.Inject

sealed interface SubmitSurveyResult {

    data class Submitted(val score: Double) : SubmitSurveyResult

    /** Còn câu chưa đạt yêu cầu — không ghi gì cả. */
    data class Invalid(val issues: List<SurveyIssue>) : SubmitSurveyResult
}

/**
 * Nộp bài khảo sát: kiểm đủ điều kiện, lưu, rồi xin đẩy lên server.
 *
 * Ba việc phải đi liền nhau. Trước đây ViewModel gọi [SurveyScorer] để kiểm rồi
 * tự nhớ gọi tiếp startUpload sau khi lưu — quên một bước thì bài khảo sát nằm
 * lại trong máy mà không ai biết.
 */
class SubmitSurveyUseCase @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val syncScheduler: SyncScheduler,
) {

    suspend operator fun invoke(
        surveyId: String,
        surveyTypeId: String,
        customerId: String,
        questions: List<SurveyQuestion>,
        answers: Map<String, SurveyAnswer>,
        note: String?,
    ): SubmitSurveyResult {
        val issues = SurveyScorer.validate(questions, answers)
        if (issues.isNotEmpty()) return SubmitSurveyResult.Invalid(issues)

        val total = surveyRepository.submit(
            surveyId = surveyId,
            surveyTypeId = surveyTypeId,
            customerId = customerId,
            questions = questions,
            answers = answers,
            note = note,
        )

        // Đẩy lên ngay. Có mạng thì bài lên trong vài giây; không mạng thì hàng
        // đợi giữ lại và tự chạy khi kết nối trở lại.
        syncScheduler.startUpload()
        return SubmitSurveyResult.Submitted(total)
    }
}
