package com.tinhcd.esalessfa.core.database.entity.master

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// =============================================================================
// Cấu hình khảo sát — tải từ server, client chỉ đọc.
//
// Bộ câu hỏi do server định nghĩa chứ không hardcode trong app: thêm câu hỏi
// hay đổi thang điểm chỉ cần sửa dữ liệu, không phải phát hành bản mới.
// =============================================================================

@Entity(tableName = "survey_types")
data class SurveyTypeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    /** Tổng điểm tối thiểu để cửa hàng được coi là đạt. */
    val passScore: Double,
)

@Entity(
    tableName = "survey_question_groups",
    indices = [Index("surveyTypeId")],
)
data class SurveyQuestionGroupEntity(
    @PrimaryKey val id: String,
    val surveyTypeId: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "survey_questions",
    indices = [Index("groupId")],
)
data class SurveyQuestionEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val code: String,
    val content: String,
    /** YES_NO | SINGLE | MULTI | NUMBER | TEXT | PHOTO */
    val answerType: String,
    val isRequired: Boolean,
    val score: Double,
    /** Số ảnh tối thiểu với câu hỏi loại PHOTO. */
    val minPhoto: Int,
    val sortOrder: Int,
)

@Entity(
    tableName = "survey_question_options",
    indices = [Index("questionId")],
)
data class SurveyQuestionOptionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val code: String,
    val content: String,
    val score: Double,
    val sortOrder: Int,
)
