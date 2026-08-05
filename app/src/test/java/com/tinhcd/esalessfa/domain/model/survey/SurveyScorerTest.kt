package com.tinhcd.esalessfa.domain.model.survey

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SurveyScorerTest {

    private fun question(
        id: String = "q1",
        type: AnswerType = AnswerType.YES_NO,
        score: Double = 20.0,
        required: Boolean = true,
        minPhoto: Int = 0,
        options: List<SurveyOption> = emptyList(),
    ) = SurveyQuestion(
        id = id,
        groupId = "g1",
        groupName = "Nhóm",
        code = id.uppercase(),
        content = "Câu hỏi $id",
        type = type,
        isRequired = required,
        score = score,
        minPhoto = minPhoto,
        options = options,
    )

    // =========================================================================
    // Chấm điểm
    // =========================================================================

    @Test
    fun `yes_no chon co duoc tron diem`() {
        val q = question(score = 20.0)
        val answers = mapOf("q1" to SurveyAnswer("q1", boolValue = true))

        val result = SurveyScorer.score(listOf(q), answers, passScore = 10.0)

        assertThat(result.total).isEqualTo(20.0)
        assertThat(result.isPassed).isTrue()
    }

    @Test
    fun `yes_no chon khong duoc khong diem`() {
        val q = question(score = 20.0)
        val answers = mapOf("q1" to SurveyAnswer("q1", boolValue = false))

        val result = SurveyScorer.score(listOf(q), answers, passScore = 10.0)

        assertThat(result.total).isEqualTo(0.0)
        assertThat(result.isPassed).isFalse()
    }

    @Test
    fun `single lay diem cua dap an chu khong phai diem cau hoi`() {
        // Kệ ngay quầy thu ngân phải hơn kệ cuối cửa hàng.
        val q = question(
            type = AnswerType.SINGLE,
            score = 15.0,
            options = listOf(
                SurveyOption("a", "A", "Quầy thu ngân", 15.0),
                SurveyOption("b", "B", "Giữa cửa hàng", 10.0),
                SurveyOption("c", "C", "Cuối cửa hàng", 5.0),
            ),
        )
        val answers = mapOf("q1" to SurveyAnswer("q1", selectedOptionIds = setOf("c")))

        val result = SurveyScorer.score(listOf(q), answers, passScore = 0.0)

        assertThat(result.total).isEqualTo(5.0)
    }

    @Test
    fun `multi cong don dap an nhung khong vuot diem cau hoi`() {
        val q = question(
            type = AnswerType.MULTI,
            score = 15.0,
            options = listOf(
                SurveyOption("p", "P", "Poster", 5.0),
                SurveyOption("w", "W", "Wobbler", 5.0),
                SurveyOption("s", "S", "Sticker", 5.0),
                SurveyOption("b", "B", "Banner", 5.0),
            ),
        )
        // Chọn cả 4 = 20 điểm, nhưng trần của câu là 15.
        val answers = mapOf(
            "q1" to SurveyAnswer("q1", selectedOptionIds = setOf("p", "w", "s", "b"))
        )

        val result = SurveyScorer.score(listOf(q), answers, passScore = 0.0)

        assertThat(result.total).isEqualTo(15.0)
    }

    @Test
    fun `cau text va photo khong tinh diem`() {
        val questions = listOf(
            question(id = "q1", type = AnswerType.TEXT, score = 50.0),
            question(id = "q2", type = AnswerType.PHOTO, score = 50.0),
        )
        val answers = mapOf(
            "q1" to SurveyAnswer("q1", textValue = "ghi chú"),
            "q2" to SurveyAnswer("q2", photoCount = 3),
        )

        val result = SurveyScorer.score(questions, answers, passScore = 0.0)

        assertThat(result.total).isEqualTo(0.0)
        assertThat(result.maxTotal).isEqualTo(0.0)
    }

    @Test
    fun `cau chua tra loi khong duoc diem nhung van tinh vao diem toi da`() {
        val questions = listOf(
            question(id = "q1", score = 20.0),
            question(id = "q2", score = 30.0),
        )
        val answers = mapOf("q1" to SurveyAnswer("q1", boolValue = true))

        val result = SurveyScorer.score(questions, answers, passScore = 0.0)

        assertThat(result.total).isEqualTo(20.0)
        assertThat(result.maxTotal).isEqualTo(50.0)
        assertThat(result.percent).isWithin(0.01).of(40.0)
    }

    @Test
    fun `diem toi da cua cau single lay dap an cao nhat`() {
        val q = question(
            type = AnswerType.SINGLE,
            // score của câu để 0 nhưng đáp án cao nhất là 15 — maxTotal phải là 15,
            // nếu không phần trăm hoàn thành sẽ vượt quá 100%.
            score = 0.0,
            options = listOf(
                SurveyOption("a", "A", "Tốt", 15.0),
                SurveyOption("b", "B", "Kém", 5.0),
            ),
        )
        val answers = mapOf("q1" to SurveyAnswer("q1", selectedOptionIds = setOf("a")))

        val result = SurveyScorer.score(listOf(q), answers, passScore = 0.0)

        assertThat(result.maxTotal).isEqualTo(15.0)
        assertThat(result.percent).isWithin(0.01).of(100.0)
    }

    @Test
    fun `khong co cau hoi thi phan tram bang khong chu khong chia cho khong`() {
        val result = SurveyScorer.score(emptyList(), emptyMap(), passScore = 0.0)

        assertThat(result.percent).isEqualTo(0.0)
    }

    // =========================================================================
    // Kiểm tra hợp lệ
    // =========================================================================

    @Test
    fun `bao thieu cau bat buoc chua tra loi`() {
        val questions = listOf(question(id = "q1"), question(id = "q2"))
        val answers = mapOf("q1" to SurveyAnswer("q1", boolValue = true))

        val issues = SurveyScorer.validate(questions, answers)

        assertThat(issues).hasSize(1)
        assertThat(issues.first().questionId).isEqualTo("q2")
    }

    @Test
    fun `cau khong bat buoc bo trong van hop le`() {
        val questions = listOf(question(id = "q1", required = false))

        val issues = SurveyScorer.validate(questions, emptyMap())

        assertThat(issues).isEmpty()
    }

    @Test
    fun `cau anh thieu so luong bi bao loi kem so con thieu`() {
        val q = question(id = "q1", type = AnswerType.PHOTO, minPhoto = 2)
        val answers = mapOf("q1" to SurveyAnswer("q1", photoCount = 1))

        val issues = SurveyScorer.validate(listOf(q), answers)

        assertThat(issues).hasSize(1)
        val issue = issues.first() as SurveyIssue.NotEnoughPhotos
        assertThat(issue.required).isEqualTo(2)
        assertThat(issue.actual).isEqualTo(1)
    }

    @Test
    fun `cau anh du so luong thi hop le`() {
        val q = question(id = "q1", type = AnswerType.PHOTO, minPhoto = 2)
        val answers = mapOf("q1" to SurveyAnswer("q1", photoCount = 2))

        assertThat(SurveyScorer.validate(listOf(q), answers)).isEmpty()
    }

    @Test
    fun `dat nguong pass thi duoc coi la dat`() {
        val q = question(score = 70.0)
        val answers = mapOf("q1" to SurveyAnswer("q1", boolValue = true))

        val result = SurveyScorer.score(listOf(q), answers, passScore = 70.0)

        assertThat(result.isPassed).isTrue()
    }
}
