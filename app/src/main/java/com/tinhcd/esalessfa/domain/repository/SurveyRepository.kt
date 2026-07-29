package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.survey.SurveyQuestion
import kotlinx.coroutines.flow.Flow
import java.io.File

data class SurveyTypeInfo(val id: String, val code: String, val name: String, val passScore: Double)

data class SurveyPhoto(
    val id: String,
    val questionId: String?,
    val localPath: String,
    val isUploaded: Boolean,
)

interface SurveyRepository {

    suspend fun types(): List<SurveyTypeInfo>

    suspend fun questions(surveyTypeId: String): List<SurveyQuestion>

    /** Bài khảo sát đang làm dở của khách hàng hôm nay, tạo mới nếu chưa có. */
    suspend fun startDraft(surveyTypeId: String, customerId: String): String

    fun observePhotos(surveyId: String): Flow<List<SurveyPhoto>>

    /**
     * Xử lý ảnh vừa chụp rồi xếp vào hàng đợi upload.
     *
     * Ảnh được nén và đóng dấu NGAY tại đây thay vì lúc upload: làm sớm thì
     * dung lượng máy không phình theo số ảnh chờ, và dấu thời gian đúng với lúc
     * chụp chứ không phải lúc có sóng.
     */
    suspend fun addPhoto(
        surveyId: String,
        questionId: String?,
        rawFile: File,
        location: GeoPoint?,
        customerName: String,
    )

    suspend fun removePhoto(photoId: String)

    /** Hoàn tất bài khảo sát: chấm điểm, lưu, xếp vào outbox. */
    suspend fun submit(
        surveyId: String,
        surveyTypeId: String,
        customerId: String,
        questions: List<SurveyQuestion>,
        answers: Map<String, SurveyAnswer>,
        note: String?,
    ): Double
}
