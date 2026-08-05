package com.tinhcd.esalessfa.domain.model.survey

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

/** Loại bài khảo sát cùng điểm sàn để coi là đạt. */
data class SurveyTypeInfo(val id: String, val code: String, val name: String, val passScore: Double)

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

/** Ảnh minh chứng của một bài khảo sát. */
data class SurveyPhoto(
    val id: String,
    val questionId: String?,
    val localPath: String,
    val isUploaded: Boolean,
)

data class SurveyScore(
    val total: Double,
    val maxTotal: Double,
    val isPassed: Boolean,
) {
    val percent: Double get() = if (maxTotal <= 0) 0.0 else total / maxTotal * 100
}
