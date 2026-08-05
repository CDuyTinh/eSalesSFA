package com.tinhcd.esalessfa.domain.model.survey

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
