package com.tinhcd.esalessfa.feature.survey

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.model.survey.SurveyIssue
import com.tinhcd.esalessfa.domain.model.survey.SurveyPhoto
import com.tinhcd.esalessfa.domain.model.survey.SurveyQuestion
import com.tinhcd.esalessfa.domain.model.survey.SurveyScore
import com.tinhcd.esalessfa.domain.model.survey.SurveyScorer
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.usecase.AddSurveyPhotoUseCase
import com.tinhcd.esalessfa.domain.usecase.StartSurveyUseCase
import com.tinhcd.esalessfa.domain.usecase.SubmitSurveyResult
import com.tinhcd.esalessfa.domain.usecase.SubmitSurveyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val startSurvey: StartSurveyUseCase,
    private val addSurveyPhoto: AddSurveyPhotoUseCase,
    private val submitSurvey: SubmitSurveyUseCase,
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
            val draft = startSurvey(surveyTypeId = surveyTypeId, customerId = customerId)
            surveyId = draft.surveyId
            passScore = draft.passScore

            _uiState.update {
                it.copy(customer = draft.customer, questions = draft.questions, isLoading = false)
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

    fun addPhoto(questionId: String?, file: File) {
        viewModelScope.launch {
            runCatching {
                addSurveyPhoto(
                    surveyId = surveyId,
                    questionId = questionId,
                    rawFile = file,
                    customerName = _uiState.value.customer?.name.orEmpty(),
                )
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

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                submitSurvey(
                    surveyId = surveyId,
                    surveyTypeId = surveyTypeId,
                    customerId = customerId,
                    questions = state.questions,
                    answers = state.answers,
                    note = note,
                )
            }.onSuccess { result ->
                _uiState.update { it.copy(isSaving = false) }
                when (result) {
                    is SubmitSurveyResult.Submitted ->
                        _events.send(SurveyFormEvent.Submitted(result.score))

                    // Giữ lại danh sách câu chưa đạt để adapter tô đỏ đúng chỗ.
                    is SubmitSurveyResult.Invalid -> {
                        _uiState.update { it.copy(issues = result.issues) }
                        _events.send(SurveyFormEvent.Invalid(result.issues))
                    }
                }
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
