package com.tinhcd.esalessfa.feature.survey

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.core.location.LocationProvider
import com.tinhcd.esalessfa.core.ui.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentSurveyFormBinding
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.survey.SurveyIssue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class SurveyFormFragment : Fragment(R.layout.fragment_survey_form) {

    private val viewModel: SurveyFormViewModel by viewModels()

    @Inject lateinit var locationProvider: LocationProvider

    /** Câu hỏi đang chờ ảnh trả về từ màn camera. */
    private var pendingPhotoQuestionId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSurveyFormBinding.bind(view)

        view.padTopForStatusBar()

        val adapter = SurveyFormAdapter(
            FormCallbacks(
                onAnswerChanged = viewModel::onAnswerChanged,
                onCapture = { questionId ->
                    pendingPhotoQuestionId = questionId
                    findNavController().navigate(R.id.action_surveyForm_to_camera)
                },
                onRemovePhoto = viewModel::removePhoto,
            )
        )

        binding.questionList.layoutManager = LinearLayoutManager(requireContext())
        binding.questionList.adapter = adapter
        binding.questionList.itemAnimator = null

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.submitButton.setOnClickListener { viewModel.submit(note = null) }

        observeCameraResult()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        binding.toolbar.subtitle = state.customer?.name

                        val issuesById = state.issues.associateBy { it.questionId }
                        adapter.submitList(state.questions.toFormRows(state, issuesById))

                        binding.scoreText.text = getString(
                            R.string.survey_score,
                            state.score.total.roundToInt(),
                            state.score.maxTotal.roundToInt(),
                            state.score.percent.roundToInt(),
                        )
                        binding.passBadge.visibility =
                            if (state.score.isPassed) View.VISIBLE else View.GONE
                        binding.submitButton.isEnabled = !state.isSaving
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SurveyFormEvent.Submitted -> {
                                Snackbar.make(
                                    view,
                                    getString(R.string.survey_submitted, event.score.roundToInt()),
                                    Snackbar.LENGTH_LONG,
                                ).show()
                                findNavController().navigateUp()
                            }

                            is SurveyFormEvent.Invalid -> {
                                Snackbar.make(
                                    view,
                                    getString(R.string.survey_invalid, event.issues.size),
                                    Snackbar.LENGTH_LONG,
                                ).show()
                                // Cuộn tới câu đầu tiên chưa đạt thay vì để nhân
                                // viên tự dò trong danh sách hàng chục câu.
                                val firstId = event.issues.firstOrNull()?.questionId
                                val index = adapter.currentList.indexOfFirst {
                                    it is FormRow.Question && it.question.id == firstId
                                }
                                if (index >= 0) binding.questionList.smoothScrollToPosition(index)
                            }

                            is SurveyFormEvent.Error ->
                                Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun observeCameraResult() {
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>(CameraFragment.RESULT_PHOTO_PATH)
            ?.observe(viewLifecycleOwner) { path ->
                val questionId = pendingPhotoQuestionId
                pendingPhotoQuestionId = null
                findNavController().currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<String>(CameraFragment.RESULT_PHOTO_PATH)

                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.addPhoto(questionId, File(path), currentLocation())
                }
            }
    }

    /**
     * Lấy vị trí để đóng dấu lên ảnh.
     *
     * Chờ tối đa 3 giây rồi thôi: không có toạ độ thì ảnh vẫn phải chụp được,
     * chỉ là dấu thiếu một dòng. Chặn nhân viên đứng đợi GPS giữa cửa hàng thì
     * tệ hơn nhiều.
     */
    private suspend fun currentLocation(): GeoPoint? =
        if (!locationProvider.hasPermission()) {
            null
        } else {
            withTimeoutOrNull(3_000) {
                locationProvider.locationUpdates().firstOrNull()?.point
            }
        }

    private fun List<com.tinhcd.esalessfa.domain.survey.SurveyQuestion>.toFormRows(
        state: SurveyFormUiState,
        issues: Map<String, SurveyIssue>,
    ): List<FormRow> = buildList {
        var lastGroup: String? = null
        this@toFormRows.forEach { question ->
            if (question.groupName != lastGroup) {
                add(FormRow.Group(question.groupName))
                lastGroup = question.groupName
            }
            add(
                FormRow.Question(
                    question = question,
                    answer = state.answers[question.id],
                    photos = state.photosOf(question.id),
                    issue = issues[question.id],
                )
            )
        }
    }
}
