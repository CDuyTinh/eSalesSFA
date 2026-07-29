package com.tinhcd.esalessfa.domain.survey

/**
 * Kiểu câu trả lời. Mỗi kiểu ứng với một viewType trong form động, nên thêm kiểu
 * mới chỉ cần thêm một nhánh chứ không đụng phần còn lại.
 */
enum class AnswerType {
    YES_NO, SINGLE, MULTI, NUMBER, TEXT, PHOTO;

    companion object {
        fun from(value: String): AnswerType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

data class SurveyOption(
    val id: String,
    val code: String,
    val content: String,
    val score: Double,
)

data class SurveyQuestion(
    val id: String,
    val groupId: String,
    val groupName: String,
    val code: String,
    val content: String,
    val type: AnswerType,
    val isRequired: Boolean,
    /** Điểm tối đa cho câu này; câu SINGLE/MULTI lấy điểm từ đáp án. */
    val score: Double,
    val minPhoto: Int,
    val options: List<SurveyOption> = emptyList(),
)

/**
 * Câu trả lời của một câu hỏi.
 *
 * Dùng một lớp cho mọi kiểu thay vì sealed class: form động phải lưu và đọc
 * đồng loạt, và việc map sang bảng survey_answers vốn cũng là một hàng phẳng.
 */
data class SurveyAnswer(
    val questionId: String,
    val boolValue: Boolean? = null,
    val selectedOptionIds: Set<String> = emptySet(),
    val numberValue: Double? = null,
    val textValue: String? = null,
    val photoCount: Int = 0,
) {
    fun isAnswered(type: AnswerType): Boolean = when (type) {
        AnswerType.YES_NO -> boolValue != null
        AnswerType.SINGLE, AnswerType.MULTI -> selectedOptionIds.isNotEmpty()
        AnswerType.NUMBER -> numberValue != null
        AnswerType.TEXT -> !textValue.isNullOrBlank()
        AnswerType.PHOTO -> photoCount > 0
    }
}

/** Lý do một câu chưa hợp lệ, để UI chỉ đúng chỗ cần sửa. */
sealed interface SurveyIssue {
    val questionId: String

    data class Missing(override val questionId: String, val questionCode: String) : SurveyIssue

    data class NotEnoughPhotos(
        override val questionId: String,
        val questionCode: String,
        val required: Int,
        val actual: Int,
    ) : SurveyIssue
}

data class SurveyScore(
    val total: Double,
    val maxTotal: Double,
    val isPassed: Boolean,
) {
    val percent: Double get() = if (maxTotal <= 0) 0.0 else total / maxTotal * 100
}

object SurveyScorer {

    /**
     * Chấm điểm bài khảo sát.
     *
     * Quy tắc theo từng kiểu:
     *  - YES_NO  : chọn "Có" được trọn điểm, "Không" được 0.
     *  - SINGLE  : lấy điểm của đáp án đã chọn, không phải điểm của câu hỏi —
     *              "kệ ngay quầy thu ngân" phải hơn "kệ cuối cửa hàng".
     *  - MULTI   : cộng điểm mọi đáp án đã chọn, nhưng KHÔNG vượt điểm câu hỏi.
     *  - NUMBER  : có nhập là được điểm; ngưỡng cụ thể do server cấu hình sau.
     *  - TEXT    : luôn 0 điểm, chỉ để ghi chú.
     *  - PHOTO   : luôn 0 điểm, giá trị nằm ở ảnh minh chứng.
     */
    fun score(
        questions: List<SurveyQuestion>,
        answers: Map<String, SurveyAnswer>,
        passScore: Double,
    ): SurveyScore {
        var total = 0.0
        var maxTotal = 0.0

        for (question in questions) {
            maxTotal += maxScoreOf(question)
            val answer = answers[question.id] ?: continue

            total += when (question.type) {
                AnswerType.YES_NO -> if (answer.boolValue == true) question.score else 0.0

                AnswerType.SINGLE -> question.options
                    .firstOrNull { it.id in answer.selectedOptionIds }
                    ?.score ?: 0.0

                AnswerType.MULTI -> question.options
                    .filter { it.id in answer.selectedOptionIds }
                    .sumOf { it.score }
                    .coerceAtMost(question.score)

                AnswerType.NUMBER -> if (answer.numberValue != null) question.score else 0.0

                AnswerType.TEXT, AnswerType.PHOTO -> 0.0
            }
        }

        return SurveyScore(
            total = total,
            maxTotal = maxTotal,
            isPassed = total >= passScore,
        )
    }

    /**
     * Điểm tối đa của một câu.
     *
     * Câu SINGLE lấy đáp án cao điểm nhất chứ không lấy [SurveyQuestion.score],
     * nếu không thì phần trăm hoàn thành sẽ sai khi hai con số lệch nhau.
     */
    private fun maxScoreOf(question: SurveyQuestion): Double = when (question.type) {
        AnswerType.SINGLE -> question.options.maxOfOrNull { it.score } ?: question.score
        AnswerType.MULTI -> question.score
        AnswerType.TEXT, AnswerType.PHOTO -> 0.0
        else -> question.score
    }

    /** Các câu chưa đạt yêu cầu để nộp bài. */
    fun validate(
        questions: List<SurveyQuestion>,
        answers: Map<String, SurveyAnswer>,
    ): List<SurveyIssue> = buildList {
        for (question in questions) {
            val answer = answers[question.id]

            // Câu ảnh kiểm tra SỐ LƯỢNG ảnh, không chỉ kiểm tra có hay không:
            // yêu cầu 2 ảnh mà chụp 1 thì vẫn thiếu bằng chứng.
            if (question.type == AnswerType.PHOTO && question.minPhoto > 0) {
                val count = answer?.photoCount ?: 0
                if (count < question.minPhoto) {
                    add(
                        SurveyIssue.NotEnoughPhotos(
                            question.id, question.code, question.minPhoto, count
                        )
                    )
                }
                continue
            }

            if (question.isRequired && answer?.isAnswered(question.type) != true) {
                add(SurveyIssue.Missing(question.id, question.code))
            }
        }
    }
}
