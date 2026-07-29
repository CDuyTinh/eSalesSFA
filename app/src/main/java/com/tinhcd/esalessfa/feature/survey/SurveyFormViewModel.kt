package com.tinhcd.esalessfa.feature.survey

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.core.media.PhotoUploadManager
import com.tinhcd.esalessfa.core.sync.SyncManager
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SurveyPhoto
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.survey.SurveyIssue
import com.tinhcd.esalessfa.domain.survey.SurveyQuestion
import com.tinhcd.esalessfa.domain.survey.SurveyScore
import com.tinhcd.esalessfa.domain.survey.SurveyScorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SurveyFormUiState(
    val customer: Customer? = null,
    val questions: List<SurveyQuestion> = emptyList(),
    val answers: Map<String, SurveyAnswer> = emptyMap(),
    val photos: List<SurveyPhoto> = emptyList(),
    val score: SurveyScore = SurveyScore(0.0, 0.0, false),
    val issues: List<SurveyIssue> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
) {
    fun photosOf(questionId: String): List<SurveyPhoto> =
        photos.filter { it.questionId == questionId }
}

sealed interface SurveyFormEvent {
    data class Submitted(val score: Double) : SurveyFormEvent
    data class Invalid(val issues: List<SurveyIssue>) : SurveyFormEvent
    data class Error(val message: String) : SurveyFormEvent
}

@HiltViewModel
class SurveyFormViewModel @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val customerRepository: CustomerRepository,
    private val photoUploadManager: PhotoUploadManager,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[ARG_CUSTOMER_ID] ?: ""
    private val surveyTypeId: String = savedStateHandle[ARG_SURVEY_TYPE_ID] ?: ""

    private val _uiState = MutableStateFlow(SurveyFormUiState())
    val uiState: StateFlow<SurveyFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<SurveyFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var surveyId: String = ""
    private var passScore: Double = 0.0

    init {
        viewModelScope.launch {
            val customer = customerRepository.getById(customerId)
            val questions = surveyRepository.questions(surveyTypeId)
            passScore = surveyRepository.types()
                .firstOrNull { it.id == surveyTypeId }?.passScore ?: 0.0

            surveyId = surveyRepository.startDraft(surveyTypeId, customerId)

            _uiState.update {
                it.copy(customer = customer, questions = questions, isLoading = false)
            }

            surveyRepository.observePhotos(surveyId).collect { photos ->
                _uiState.update { state ->
                    // Số ảnh nằm trong câu trả lời để SurveyScorer kiểm tra được
                    // yêu cầu tối thiểu mà không cần biết gì về tầng lưu trữ.
                    val withPhotoCounts = state.answers.toMutableMap()
                    state.questions.forEach { q ->
                        val count = photos.count { it.questionId == q.id }
                        val existing = withPhotoCounts[q.id] ?: SurveyAnswer(q.id)
                        withPhotoCounts[q.id] = existing.copy(photoCount = count)
                    }
                    state.copy(photos = photos, answers = withPhotoCounts).rescored()
                }
            }
        }
    }

    fun onAnswerChanged(questionId: String, transform: (SurveyAnswer) -> SurveyAnswer) {
        _uiState.update { state ->
            val current = state.answers[questionId] ?: SurveyAnswer(questionId)
            state.copy(answers = state.answers + (questionId to transform(current))).rescored()
        }
    }

    fun addPhoto(questionId: String?, file: File, location: GeoPoint?) {
        viewModelScope.launch {
            runCatching {
                surveyRepository.addPhoto(
                    surveyId = surveyId,
                    questionId = questionId,
                    rawFile = file,
                    location = location,
                    customerName = _uiState.value.customer?.name.orEmpty(),
                )
            }.onSuccess {
                // Upload ngay: ảnh lên dần trong lúc nhân viên còn trả lời tiếp,
                // nên lúc bấm hoàn tất thường đã xong hết.
                photoUploadManager.start()
            }.onFailure { e ->
                _events.send(SurveyFormEvent.Error(e.message ?: "Không xử lý được ảnh"))
            }
        }
    }

    fun removePhoto(photoId: String) {
        viewModelScope.launch { surveyRepository.removePhoto(photoId) }
    }

    fun submit(note: String?) {
        val state = _uiState.value
        if (state.isSaving) return

        val issues = SurveyScorer.validate(state.questions, state.answers)
        if (issues.isNotEmpty()) {
            _uiState.update { it.copy(issues = issues) }
            viewModelScope.launch { _events.send(SurveyFormEvent.Invalid(issues)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                surveyRepository.submit(
                    surveyId = surveyId,
                    surveyTypeId = surveyTypeId,
                    customerId = customerId,
                    questions = state.questions,
                    answers = state.answers,
                    note = note,
                )
            }.onSuccess { total ->
                _uiState.update { it.copy(isSaving = false) }
                syncManager.startUpload()
                _events.send(SurveyFormEvent.Submitted(total))
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _events.send(SurveyFormEvent.Error(e.message ?: "Không lưu được bài khảo sát"))
            }
        }
    }

    private fun SurveyFormUiState.rescored(): SurveyFormUiState =
        copy(score = SurveyScorer.score(questions, answers, passScore))

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
        const val ARG_SURVEY_TYPE_ID = "surveyTypeId"
    }
}
