package com.tinhcd.esalessfa.feature.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentSyncBinding
import com.tinhcd.esalessfa.databinding.ItemSyncStepBinding
import com.tinhcd.esalessfa.domain.model.sync.SyncGroup
import com.tinhcd.esalessfa.domain.model.sync.SyncPhase
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SyncFragment : Fragment(R.layout.fragment_sync) {

    private val viewModel: SyncViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSyncBinding.bind(view)

        binding.title.padTopForStatusBar()

        // KEEP policy nên gọi lại lúc xoay máy cũng không tạo thêm lượt nào.
        viewModel.start()

        binding.retryButton.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bind(binding, state)
                    if (state.isCompleted) viewModel.onSyncCompleted()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finished.collect { finished ->
                    if (!finished) return@collect
                    // Lượt đầu sau đăng nhập thì đi tiếp vào Home; lượt do người
                    // dùng chủ động thì quay lại chỗ họ vừa đứng.
                    when (viewModel.mode) {
                        SyncMode.FIRST_RUN ->
                            findNavController().navigate(R.id.action_sync_to_home)

                        SyncMode.MANUAL -> findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun bind(binding: FragmentSyncBinding, state: SyncUiState) {
        val number = NumberFormat.getInstance(Locale("vi", "VN"))

        binding.phaseText.setText(state.phaseLabel())
        binding.percentText.text = getString(R.string.percent_value, state.percent)

        // Chưa đếm được phần nào — đang xếp hàng chờ chạy, hoặc đang ở bước gửi
        // lên — thì cho thanh chạy vô định. Để nó đứng im ở 0% thì nhìn y hệt
        // màn hình bị treo, đúng thứ người dùng đang phàn nàn.
        val indeterminate = state.isRunning && state.partsDone == 0
        binding.progressBar.isIndeterminate = indeterminate
        if (!indeterminate) binding.progressBar.setProgressCompat(state.percent, true)

        binding.partsText.text = if (state.totalRows > 0) {
            getString(
                R.string.sync_parts,
                state.partsDone,
                state.partsTotal,
                number.format(state.totalRows),
            )
        } else {
            getString(R.string.sync_parts_plain, state.partsDone, state.partsTotal)
        }

        binding.stepContainer.bindSteps(state)

        binding.errorText.text = state.errorMessage
        binding.errorText.isVisible = state.errorMessage != null
        binding.retryButton.isVisible = state.errorMessage != null
    }

    /**
     * Vẽ lại cả danh sách mỗi lần trạng thái đổi.
     *
     * Sáu dòng thì dựng lại rẻ hơn nhiều so với nuôi một adapter chỉ để đổi vài
     * ký tự — màn Tổng quan cũng đang vẽ bảng xếp hạng theo cách này.
     *
     * Chỉ hai trạng thái "xong" và "đang tải", không có "chờ tới lượt": server
     * tải song song toàn bộ bảng nên nhiều nhóm chạy cùng lúc, và nhóm chưa
     * xong là nhóm đang được tải chứ không phải đang nằm đợi.
     */
    private fun android.view.ViewGroup.bindSteps(state: SyncUiState) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)

        SyncGroup.entries.forEach { group ->
            val row = ItemSyncStepBinding.inflate(inflater, this, false)
            val isDone = group.isDone(state.doneTables)

            row.label.setText(group.label())
            row.mark.setText(if (isDone) R.string.sync_mark_done else R.string.sync_mark_running)
            row.state.setText(if (isDone) R.string.sync_step_done else R.string.sync_step_running)

            val color = if (isDone) R.color.stateGreen else R.color.brandRed
            row.mark.setTextColor(ContextCompat.getColor(context, color))
            row.state.setTextColor(ContextCompat.getColor(context, color))

            addView(row.root)
        }
    }

    @StringRes
    private fun SyncUiState.phaseLabel(): Int = when {
        errorMessage != null -> R.string.sync_phase_failed
        isCompleted -> R.string.sync_phase_done
        phase == SyncPhase.UPLOAD -> R.string.sync_phase_upload

        // Lượt đầu sau đăng nhập không có bước gửi lên nào, gọi nó là "bước 2/2"
        // thì người dùng sẽ đi tìm bước 1 không tồn tại.
        viewModel.mode == SyncMode.FIRST_RUN -> R.string.sync_phase_download_only
        else -> R.string.sync_phase_download
    }

    @StringRes
    private fun SyncGroup.label(): Int = when (this) {
        SyncGroup.CATALOG -> R.string.sync_group_catalog
        SyncGroup.CUSTOMER -> R.string.sync_group_customer
        SyncGroup.PRODUCT -> R.string.sync_group_product
        SyncGroup.PRICING -> R.string.sync_group_pricing
        SyncGroup.SURVEY -> R.string.sync_group_survey
        SyncGroup.ORDER_HISTORY -> R.string.sync_group_order_history
    }
}
