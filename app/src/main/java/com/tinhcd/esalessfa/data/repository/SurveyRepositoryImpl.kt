package com.tinhcd.esalessfa.data.repository

import android.content.Context
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.dao.SalespersonDao
import com.tinhcd.esalessfa.core.database.dao.PendingUploadDao
import com.tinhcd.esalessfa.core.database.dao.SurveyConfigDao
import com.tinhcd.esalessfa.core.database.dao.SurveyResultDao
import com.tinhcd.esalessfa.core.database.dao.VisitDao
import com.tinhcd.esalessfa.core.database.entity.local.PendingUploadEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyAnswerEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyPhotoEntity
import com.tinhcd.esalessfa.core.media.ImageProcessor
import com.tinhcd.esalessfa.core.media.PhotoUploadWorker
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.repository.SurveyPhoto
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.repository.SurveyTypeInfo
import com.tinhcd.esalessfa.domain.survey.AnswerType
import com.tinhcd.esalessfa.domain.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.survey.SurveyOption
import com.tinhcd.esalessfa.domain.survey.SurveyQuestion
import com.tinhcd.esalessfa.domain.survey.SurveyScorer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SurveyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configDao: SurveyConfigDao,
    private val resultDao: SurveyResultDao,
    private val pendingUploadDao: PendingUploadDao,
    private val salespersonDao: SalespersonDao,
    private val visitDao: VisitDao,
    private val imageProcessor: ImageProcessor,
) : SurveyRepository {

    override suspend fun types(): List<SurveyTypeInfo> =
        configDao.types().map { SurveyTypeInfo(it.id, it.code, it.name, it.passScore) }

    override suspend fun questions(surveyTypeId: String): List<SurveyQuestion> {
        val rows = configDao.questions(surveyTypeId)
        if (rows.isEmpty()) return emptyList()

        // Một truy vấn cho toàn bộ đáp án thay vì một truy vấn mỗi câu hỏi.
        val optionsByQuestion = configDao.options(rows.map { it.id }).groupBy { it.questionId }

        return rows.mapNotNull { row ->
            val type = AnswerType.from(row.answerType) ?: return@mapNotNull null
            SurveyQuestion(
                id = row.id,
                groupId = row.groupId,
                groupName = row.groupName,
                code = row.code,
                content = row.content,
                type = type,
                isRequired = row.isRequired,
                score = row.score,
                minPhoto = row.minPhoto,
                options = optionsByQuestion[row.id].orEmpty().map {
                    SurveyOption(it.id, it.code, it.content, it.score)
                },
            )
        }
    }

    override suspend fun startDraft(surveyTypeId: String, customerId: String): String {
        val salesperson = requireNotNull(salespersonDao.getCurrent()) {
            "Chưa có hồ sơ nhân viên — cần đồng bộ trước khi khảo sát"
        }
        val date = today()
        val id = UUID.randomUUID().toString()

        resultDao.upsertHeader(
            SurveyEntity(
                id = id,
                surveyTypeId = surveyTypeId,
                customerId = customerId,
                salespersonId = salesperson.id,
                visitId = visitDao.getOpenVisit(customerId, date)?.id,
                surveyDate = date,
                totalScore = 0.0,
                isPassed = false,
                note = null,
                // DRAFT: ảnh có thể chụp trước khi trả lời hết câu hỏi, nhưng bài
                // chưa được đẩy lên cho tới khi nhân viên bấm hoàn tất.
                syncStatus = SyncStatus.DRAFT,
                clientCreatedAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    override fun observePhotos(surveyId: String): Flow<List<SurveyPhoto>> =
        resultDao.observePhotos(surveyId).map { photos ->
            photos.map { SurveyPhoto(it.id, it.questionId, it.localPath, it.storagePath != null) }
        }

    override suspend fun addPhoto(
        surveyId: String,
        questionId: String?,
        rawFile: File,
        location: GeoPoint?,
        customerName: String,
    ) {
        val salesperson = salespersonDao.getCurrent() ?: return
        val photoId = UUID.randomUUID().toString()

        val target = File(File(context.cacheDir, "survey-photos"), "$photoId.jpg")
        val processed = imageProcessor.process(rawFile, target, location, customerName)
        rawFile.delete()

        val remotePath = "${salesperson.id}/$surveyId/$photoId.jpg"

        resultDao.upsertPhoto(
            SurveyPhotoEntity(
                id = photoId,
                surveyId = surveyId,
                questionId = questionId,
                localPath = processed.file.absolutePath,
                storagePath = null,
                latitude = location?.latitude,
                longitude = location?.longitude,
                takenAt = System.currentTimeMillis(),
                fileSize = processed.sizeBytes,
            )
        )

        pendingUploadDao.upsert(
            PendingUploadEntity(
                id = UUID.randomUUID().toString(),
                entityType = "SURVEY_PHOTO",
                entityId = photoId,
                localPath = processed.file.absolutePath,
                bucket = PhotoUploadWorker.BUCKET_SURVEY_PHOTOS,
                remotePath = remotePath,
                fileSize = processed.sizeBytes.toLong(),
                status = PhotoUploadWorker.STATUS_PENDING,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun removePhoto(photoId: String) = resultDao.deletePhoto(photoId)

    override suspend fun submit(
        surveyId: String,
        surveyTypeId: String,
        customerId: String,
        questions: List<SurveyQuestion>,
        answers: Map<String, SurveyAnswer>,
        note: String?,
    ): Double {
        val salesperson = requireNotNull(salespersonDao.getCurrent())
        val passScore = configDao.typeById(surveyTypeId)?.passScore ?: 0.0
        val score = SurveyScorer.score(questions, answers, passScore)

        val header = SurveyEntity(
            id = surveyId,
            surveyTypeId = surveyTypeId,
            customerId = customerId,
            salespersonId = salesperson.id,
            visitId = visitDao.getOpenVisit(customerId, today())?.id,
            surveyDate = today(),
            totalScore = score.total,
            isPassed = score.isPassed,
            note = note,
            syncStatus = SyncStatus.PENDING,
            clientCreatedAt = System.currentTimeMillis(),
        )

        // Câu MULTI sinh nhiều dòng, mỗi dòng một đáp án — giữ được cấu trúc
        // quan hệ thay vì nhồi danh sách id vào một cột text.
        val rows = questions.flatMap { question ->
            val answer = answers[question.id] ?: return@flatMap emptyList()

            when (question.type) {
                AnswerType.SINGLE, AnswerType.MULTI -> answer.selectedOptionIds.map { optionId ->
                    answerRow(
                        surveyId, question.id, optionId = optionId,
                        score = question.options.firstOrNull { it.id == optionId }?.score ?: 0.0,
                    )
                }

                AnswerType.YES_NO -> listOf(
                    answerRow(
                        surveyId, question.id,
                        bool = answer.boolValue,
                        score = if (answer.boolValue == true) question.score else 0.0,
                    )
                )

                AnswerType.NUMBER -> listOf(
                    answerRow(
                        surveyId, question.id,
                        number = answer.numberValue,
                        score = if (answer.numberValue != null) question.score else 0.0,
                    )
                )

                AnswerType.TEXT -> listOf(
                    answerRow(surveyId, question.id, text = answer.textValue, score = 0.0)
                )

                AnswerType.PHOTO -> emptyList()
            }
        }

        resultDao.save(header, rows, emptyList())
        return score.total
    }

    private fun answerRow(
        surveyId: String,
        questionId: String,
        optionId: String? = null,
        bool: Boolean? = null,
        number: Double? = null,
        text: String? = null,
        score: Double,
    ) = SurveyAnswerEntity(
        id = UUID.randomUUID().toString(),
        surveyId = surveyId,
        questionId = questionId,
        optionId = optionId,
        answerText = text,
        answerValue = number,
        answerBool = bool,
        score = score,
    )

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
