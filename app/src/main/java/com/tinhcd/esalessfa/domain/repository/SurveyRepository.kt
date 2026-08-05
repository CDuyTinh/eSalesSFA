package com.tinhcd.esalessfa.domain.repository

import com.tinhcd.esalessfa.domain.model.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.model.survey.SurveyPhoto
import com.tinhcd.esalessfa.domain.model.survey.SurveyQuestion
import com.tinhcd.esalessfa.domain.model.survey.SurveyTypeInfo
import java.io.File
import kotlinx.coroutines.flow.Flow

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
