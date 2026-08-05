package com.tinhcd.esalessfa.feature.customer.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentCustomerWorkTabBinding
import com.tinhcd.esalessfa.databinding.ItemCustomerTaskBinding
import com.tinhcd.esalessfa.domain.model.visit.VisitGate
import com.tinhcd.esalessfa.feature.common.showOptionSheet
import com.tinhcd.esalessfa.feature.inventory.StockCountViewModel
import com.tinhcd.esalessfa.feature.order.edit.OrderEditViewModel
import com.tinhcd.esalessfa.feature.survey.SurveyFormViewModel
import com.tinhcd.esalessfa.feature.visit.CheckInViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Tab "Công việc": những việc làm được trong lượt ghé đang mở.
 *
 * Tab này chỉ được màn chủ tạo ra khi đang check-in TẠI cửa hàng đang xem, nên ở
 * đây không kiểm tra lại điều kiện — kiểm hai lần ở hai nơi là cách chắc chắn để
 * hai nơi lệch nhau về sau.
 */
@AndroidEntryPoint
class CustomerWorkTabFragment : Fragment(R.layout.fragment_customer_work_tab) {

    private val viewModel: CustomerDetailViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    /** Một việc trong lượt ghé. */
    private data class Task(
        @StringRes val title: Int,
        @DrawableRes val icon: Int,
        val action: () -> Unit,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerWorkTabBinding.bind(view)

        bindTasks(binding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.gate.collect { gate ->
                    val visit = (gate as? VisitGate.CheckedInHere)?.visit ?: return@collect
                    binding.visitHint.text = getString(
                        R.string.customer_visit_since,
                        TIME_FORMAT.format(Date(visit.checkInAt)),
                    )
                }
            }
        }
    }

    private fun bindTasks(binding: FragmentCustomerWorkTabBinding) {
        val tasks = listOf(
            Task(R.string.action_take_order, R.drawable.ic_task_order, ::openOrder),
            Task(R.string.action_stock_count, R.drawable.ic_task_stock, ::openStockCount),
            Task(R.string.action_survey, R.drawable.ic_task_survey, ::openSurvey),
            Task(R.string.action_check_out, R.drawable.ic_task_checkout, ::openVisit),
        )

        val inflater = LayoutInflater.from(requireContext())
        binding.taskGrid.removeAllViews()

        tasks.forEach { task ->
            val cell = ItemCustomerTaskBinding.inflate(inflater, binding.taskGrid, false)
            cell.taskIcon.setImageResource(task.icon)
            cell.taskTitle.setText(task.title)
            cell.root.setOnClickListener { task.action() }

            // columnWeight = 1: ba cột chia đều bề ngang. Thiếu nó thì GridLayout
            // để mỗi ô rộng đúng bằng nội dung và lưới lệch hẳn về bên trái.
            cell.root.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f)
            }
            binding.taskGrid.addView(cell.root)
        }
    }

    private fun customerId(): String? = viewModel.customer.value?.id

    private fun openOrder() {
        val id = customerId() ?: return
        findNavController().navigate(
            R.id.action_customerDetail_to_orderEdit,
            bundleOf(OrderEditViewModel.ARG_CUSTOMER_ID to id),
        )
    }

    private fun openStockCount() {
        val id = customerId() ?: return
        findNavController().navigate(
            R.id.action_customerDetail_to_stockCount,
            bundleOf(StockCountViewModel.ARG_CUSTOMER_ID to id),
        )
    }

    private fun openVisit() {
        val id = customerId() ?: return
        findNavController().navigate(
            R.id.action_customerDetail_to_checkIn,
            bundleOf(CheckInViewModel.ARG_CUSTOMER_ID to id),
        )
    }

    /**
     * Một loại khảo sát thì vào thẳng, đừng bắt người dùng chọn giữa một lựa chọn.
     */
    private fun openSurvey() {
        val id = customerId() ?: return
        val types = viewModel.surveyTypes.value
        val view = view ?: return

        when (types.size) {
            0 -> Snackbar.make(view, R.string.survey_no_type, Snackbar.LENGTH_SHORT).show()
            1 -> openSurvey(id, types.first().id)
            else -> showOptionSheet(
                title = R.string.survey_pick_type,
                options = types.map { it.name },
            ) { index -> openSurvey(id, types[index].id) }
        }
    }

    private fun openSurvey(customerId: String, surveyTypeId: String) {
        findNavController().navigate(
            R.id.action_customerDetail_to_survey,
            bundleOf(
                SurveyFormViewModel.ARG_CUSTOMER_ID to customerId,
                SurveyFormViewModel.ARG_SURVEY_TYPE_ID to surveyTypeId,
            ),
        )
    }

    private companion object {
        val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
