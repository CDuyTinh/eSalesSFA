package com.tinhcd.esalessfa.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.tinhcd.esalessfa.core.database.SyncStatus
import com.tinhcd.esalessfa.core.database.entity.master.SurveyQuestionOptionEntity
import com.tinhcd.esalessfa.core.database.entity.master.SurveyTypeEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyAnswerEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyEntity
import com.tinhcd.esalessfa.core.database.entity.transaction.SurveyPhotoEntity
import kotlinx.coroutines.flow.Flow

/** Câu hỏi kèm tên nhóm, để form hiển thị tiêu đề phân nhóm. */
data class SurveyQuestionRow(
    val id: String,
    val groupId: String,
    val groupName: String,
    val code: String,
    val content: String,
    val answerType: String,
    val isRequired: Boolean,
    val score: Double,
    val minPhoto: Int,
)

data class SurveyWithResults(
    @Embedded val header: SurveyEntity,
    @Relation(parentColumn = "id", entityColumn = "surveyId")
    val answers: List<SurveyAnswerEntity>,
    @Relation(parentColumn = "id", entityColumn = "surveyId")
    val photos: List<SurveyPhotoEntity>,
)

@Dao
interface SurveyConfigDao {

    @Query("SELECT * FROM survey_types ORDER BY name")
    suspend fun types(): List<SurveyTypeEntity>

    @Query("SELECT * FROM survey_types WHERE id = :id")
    suspend fun typeById(id: String): SurveyTypeEntity?

    /**
     * Toàn bộ câu hỏi của một loại khảo sát, đã sắp theo nhóm rồi tới thứ tự
     * câu — lấy một lần thay vì hỏi từng nhóm.
     */
    @Query(
        """
        SELECT q.id AS id, q.groupId AS groupId, g.name AS groupName,
               q.code AS code, q.content AS content, q.answerType AS answerType,
               q.isRequired AS isRequired, q.score AS score, q.minPhoto AS minPhoto
        FROM survey_questions q
        INNER JOIN survey_question_groups g ON g.id = q.groupId
        WHERE g.surveyTypeId = :surveyTypeId
        ORDER BY g.sortOrder, q.sortOrder
        """
    )
    suspend fun questions(surveyTypeId: String): List<SurveyQuestionRow>

    @Query("SELECT * FROM survey_question_options WHERE questionId IN (:questionIds) ORDER BY sortOrder")
    suspend fun options(questionIds: List<String>): List<SurveyQuestionOptionEntity>
}

@Dao
interface SurveyResultDao {

    @Transaction
    suspend fun save(
        header: SurveyEntity,
        answers: List<SurveyAnswerEntity>,
        photos: List<SurveyPhotoEntity>,
    ) {
        deleteAnswers(header.id)
        upsertHeader(header)
        upsertAnswers(answers)
        upsertPhotos(photos)
    }

    @Upsert suspend fun upsertHeader(header: SurveyEntity)
    @Upsert suspend fun upsertAnswers(items: List<SurveyAnswerEntity>)
    @Upsert suspend fun upsertPhotos(items: List<SurveyPhotoEntity>)
    @Upsert suspend fun upsertPhoto(item: SurveyPhotoEntity)

    @Query("DELETE FROM survey_answers WHERE surveyId = :surveyId")
    suspend fun deleteAnswers(surveyId: String)

    @Query("DELETE FROM survey_photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: String)

    @Query("SELECT * FROM survey_photos WHERE surveyId = :surveyId ORDER BY takenAt")
    fun observePhotos(surveyId: String): Flow<List<SurveyPhotoEntity>>

    @Query("UPDATE survey_photos SET storagePath = :storagePath WHERE id = :photoId")
    suspend fun markPhotoUploaded(photoId: String, storagePath: String)

    // ── outbox ──
    /**
     * Chỉ lấy bài khảo sát đã upload xong TOÀN BỘ ảnh.
     *
     * Đẩy bài lên khi ảnh còn dở sẽ tạo bản ghi survey_photos trỏ tới storage_path
     * rỗng — quản lý mở ra thấy khảo sát không có ảnh minh chứng.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM surveys
        WHERE syncStatus IN ('PENDING','FAILED') AND syncAttempts < :maxAttempts
          AND NOT EXISTS (
              SELECT 1 FROM survey_photos p
              WHERE p.surveyId = surveys.id AND p.storagePath IS NULL
          )
        ORDER BY clientCreatedAt LIMIT :limit
        """
    )
    suspend fun getPending(limit: Int = 20, maxAttempts: Int = 5): List<SurveyWithResults>

    @Query("UPDATE surveys SET syncStatus = :status, sessionId = :sessionId WHERE id IN (:ids)")
    suspend fun markStatus(ids: List<String>, status: SyncStatus, sessionId: String?)

    @Query("UPDATE surveys SET syncStatus = 'SYNCED', serverAckAt = :ackAt, lastError = NULL WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, ackAt: Long)

    @Query("UPDATE surveys SET syncStatus = 'FAILED', syncAttempts = syncAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("SELECT COUNT(*) FROM surveys WHERE syncStatus IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>
}
