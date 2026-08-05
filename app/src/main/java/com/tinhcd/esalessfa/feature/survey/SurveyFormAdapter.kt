package com.tinhcd.esalessfa.feature.survey

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.chip.Chip
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.ItemSurveyChoiceBinding
import com.tinhcd.esalessfa.databinding.ItemSurveyGroupBinding
import com.tinhcd.esalessfa.databinding.ItemSurveyInputBinding
import com.tinhcd.esalessfa.databinding.ItemSurveyPhotoBinding
import com.tinhcd.esalessfa.databinding.ItemSurveyThumbBinding
import com.tinhcd.esalessfa.domain.model.survey.AnswerType
import com.tinhcd.esalessfa.domain.model.survey.SurveyAnswer
import com.tinhcd.esalessfa.domain.model.survey.SurveyIssue
import com.tinhcd.esalessfa.domain.model.survey.SurveyPhoto
import com.tinhcd.esalessfa.domain.model.survey.SurveyQuestion
import java.io.File

/** Một dòng trong form: tiêu đề nhóm hoặc một câu hỏi. */
sealed interface FormRow {
    data class Group(val name: String) : FormRow

    data class Question(
        val question: SurveyQuestion,
        val answer: SurveyAnswer?,
        val photos: List<SurveyPhoto>,
        val issue: SurveyIssue?,
    ) : FormRow
}

/** Các thao tác form gửi ngược lên ViewModel. */
class FormCallbacks(
    val onAnswerChanged: (String, (SurveyAnswer) -> SurveyAnswer) -> Unit,
    val onCapture: (questionId: String) -> Unit,
    val onRemovePhoto: (photoId: String) -> Unit,
)

/**
 * Form khảo sát dựng động từ cấu hình server.
 *
 * Sáu kiểu câu hỏi gom vào bốn viewType: YES_NO/SINGLE/MULTI đều là chọn lựa
 * nên dùng chung ChipGroup, NUMBER/TEXT đều là ô nhập nên dùng chung layout.
 * Thêm kiểu mới chỉ cần thêm một nhánh, không đụng phần còn lại.
 */
class SurveyFormAdapter(
    private val callbacks: FormCallbacks,
) : ListAdapter<FormRow, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (val row = getItem(position)) {
        is FormRow.Group -> TYPE_GROUP
        is FormRow.Question -> when (row.question.type) {
            AnswerType.YES_NO, AnswerType.SINGLE, AnswerType.MULTI -> TYPE_CHOICE
            AnswerType.NUMBER, AnswerType.TEXT -> TYPE_INPUT
            AnswerType.PHOTO -> TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GROUP -> GroupHolder(ItemSurveyGroupBinding.inflate(inflater, parent, false))
            TYPE_CHOICE -> ChoiceHolder(
                ItemSurveyChoiceBinding.inflate(inflater, parent, false), callbacks
            )
            TYPE_INPUT -> InputHolder(
                ItemSurveyInputBinding.inflate(inflater, parent, false), callbacks
            )
            else -> PhotoHolder(
                ItemSurveyPhotoBinding.inflate(inflater, parent, false), callbacks
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is FormRow.Group -> (holder as GroupHolder).bind(row)
            is FormRow.Question -> when (holder) {
                is ChoiceHolder -> holder.bind(row)
                is InputHolder -> holder.bind(row)
                is PhotoHolder -> holder.bind(row)
            }
        }
    }

    // ── Tiêu đề nhóm ─────────────────────────────────────────────────────────
    class GroupHolder(private val binding: ItemSurveyGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: FormRow.Group) {
            binding.groupName.text = row.name
        }
    }

    // ── YES_NO / SINGLE / MULTI ──────────────────────────────────────────────
    class ChoiceHolder(
        private val binding: ItemSurveyChoiceBinding,
        private val callbacks: FormCallbacks,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: FormRow.Question) {
            val question = row.question
            binding.question.text = question.label()
            binding.showIssue(row.issue)

            val isMulti = question.type == AnswerType.MULTI
            binding.options.isSingleSelection = !isMulti
            binding.options.removeAllViews()

            val choices = when (question.type) {
                AnswerType.YES_NO -> listOf(
                    YES_ID to binding.root.context.getString(R.string.survey_yes),
                    NO_ID to binding.root.context.getString(R.string.survey_no),
                )
                else -> question.options.map { it.id to it.content }
            }

            val selected = when (question.type) {
                AnswerType.YES_NO -> when (row.answer?.boolValue) {
                    true -> setOf(YES_ID)
                    false -> setOf(NO_ID)
                    null -> emptySet()
                }
                else -> row.answer?.selectedOptionIds.orEmpty()
            }

            choices.forEach { (id, label) ->
                val chip = Chip(binding.root.context).apply {
                    text = label
                    isCheckable = true
                    isChecked = id in selected
                    setOnClickListener { onChipClicked(question, id, isChecked, isMulti) }
                }
                binding.options.addView(chip)
            }
        }

        private fun onChipClicked(
            question: SurveyQuestion,
            id: String,
            checked: Boolean,
            isMulti: Boolean,
        ) {
            callbacks.onAnswerChanged(question.id) { current ->
                when (question.type) {
                    AnswerType.YES_NO -> current.copy(boolValue = id == YES_ID)

                    AnswerType.MULTI -> current.copy(
                        selectedOptionIds = if (checked) {
                            current.selectedOptionIds + id
                        } else {
                            current.selectedOptionIds - id
                        }
                    )

                    // SINGLE: ChipGroup tự bỏ chọn cái cũ, nên chỉ giữ id mới.
                    else -> current.copy(selectedOptionIds = setOf(id))
                }
            }
        }
    }

    // ── NUMBER / TEXT ────────────────────────────────────────────────────────
    class InputHolder(
        private val binding: ItemSurveyInputBinding,
        private val callbacks: FormCallbacks,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var boundQuestion: SurveyQuestion? = null

        // Cùng kỹ thuật với lưới kiểm kê: một watcher duy nhất, gỡ ra trước khi
        // setText để thay đổi do code không bị hiểu nhầm là người dùng gõ.
        private val watcher = binding.answerInput.doAfterTextChanged { editable ->
            val question = boundQuestion ?: return@doAfterTextChanged
            val text = editable?.toString().orEmpty()
            callbacks.onAnswerChanged(question.id) { current ->
                if (question.type == AnswerType.NUMBER) {
                    current.copy(numberValue = text.toDoubleOrNull())
                } else {
                    current.copy(textValue = text)
                }
            }
        }

        fun bind(row: FormRow.Question) {
            boundQuestion = row.question
            binding.question.text = row.question.label()
            binding.showIssue(row.issue)

            binding.answerInput.inputType = if (row.question.type == AnswerType.NUMBER) {
                android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else {
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }

            val value = when (row.question.type) {
                AnswerType.NUMBER -> row.answer?.numberValue?.let {
                    if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                }
                else -> row.answer?.textValue
            }.orEmpty()

            if (binding.answerInput.text?.toString() != value) {
                binding.answerInput.removeTextChangedListener(watcher)
                binding.answerInput.setText(value)
                binding.answerInput.addTextChangedListener(watcher)
            }
        }
    }

    // ── PHOTO ────────────────────────────────────────────────────────────────
    class PhotoHolder(
        private val binding: ItemSurveyPhotoBinding,
        private val callbacks: FormCallbacks,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val thumbAdapter = ThumbAdapter(callbacks.onRemovePhoto)

        init {
            binding.photoList.layoutManager = LinearLayoutManager(
                binding.root.context, LinearLayoutManager.HORIZONTAL, false
            )
            binding.photoList.adapter = thumbAdapter
        }

        fun bind(row: FormRow.Question) {
            binding.question.text = row.question.label()
            binding.showIssue(row.issue)
            thumbAdapter.submitList(row.photos)
            binding.captureButton.setOnClickListener { callbacks.onCapture(row.question.id) }
        }
    }

    private companion object {
        const val TYPE_GROUP = 0
        const val TYPE_CHOICE = 1
        const val TYPE_INPUT = 2
        const val TYPE_PHOTO = 3

        const val YES_ID = "__yes__"
        const val NO_ID = "__no__"

        val DIFF = object : DiffUtil.ItemCallback<FormRow>() {
            override fun areItemsTheSame(old: FormRow, new: FormRow) = when {
                old is FormRow.Group && new is FormRow.Group -> old.name == new.name
                old is FormRow.Question && new is FormRow.Question ->
                    old.question.id == new.question.id
                else -> false
            }

            /**
             * Với câu nhập liệu, bỏ qua thay đổi của chính ô nhập — nếu không
             * DiffUtil rebind dòng đang gõ và con trỏ nhảy về đầu.
             */
            override fun areContentsTheSame(old: FormRow, new: FormRow): Boolean {
                if (old is FormRow.Question && new is FormRow.Question) {
                    if (old.question.type == AnswerType.NUMBER ||
                        old.question.type == AnswerType.TEXT
                    ) {
                        return old.issue == new.issue
                    }
                }
                return old == new
            }
        }
    }
}

/** Câu bắt buộc gắn dấu sao để nhân viên biết chỗ nào không được bỏ trống. */
private fun SurveyQuestion.label(): String = if (isRequired) "$content *" else content

private fun ItemSurveyChoiceBinding.showIssue(issue: SurveyIssue?) =
    issueText.applyIssue(issue, root.context)

private fun ItemSurveyInputBinding.showIssue(issue: SurveyIssue?) =
    issueText.applyIssue(issue, root.context)

private fun ItemSurveyPhotoBinding.showIssue(issue: SurveyIssue?) =
    issueText.applyIssue(issue, root.context)

private fun android.widget.TextView.applyIssue(issue: SurveyIssue?, context: android.content.Context) {
    if (issue == null) {
        visibility = View.GONE
        return
    }
    visibility = View.VISIBLE
    text = when (issue) {
        is SurveyIssue.Missing -> context.getString(R.string.survey_issue_missing)
        is SurveyIssue.NotEnoughPhotos ->
            context.getString(R.string.survey_issue_photos, issue.required, issue.actual)
    }
}

private class ThumbAdapter(
    private val onRemove: (String) -> Unit,
) : ListAdapter<SurveyPhoto, ThumbAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSurveyThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(getItem(position), onRemove)

    class Holder(private val binding: ItemSurveyThumbBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: SurveyPhoto, onRemove: (String) -> Unit) {
            binding.thumb.load(File(photo.localPath).toUri())
            // Nhãn "chờ" cho nhân viên biết ảnh chưa lên server, để họ không tắt
            // app quá sớm khi đang ở chỗ có sóng.
            binding.uploadBadge.visibility = if (photo.isUploaded) View.GONE else View.VISIBLE
            binding.removeButton.setOnClickListener { onRemove(photo.id) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SurveyPhoto>() {
            override fun areItemsTheSame(old: SurveyPhoto, new: SurveyPhoto) = old.id == new.id
            override fun areContentsTheSame(old: SurveyPhoto, new: SurveyPhoto) = old == new
        }
    }
}
