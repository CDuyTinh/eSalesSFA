package com.tinhcd.esalessfa.feature.customer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentCustomerDetailBinding
import com.tinhcd.esalessfa.domain.model.Customer
import com.tinhcd.esalessfa.domain.repository.CustomerRepository
import com.tinhcd.esalessfa.domain.repository.SurveyRepository
import com.tinhcd.esalessfa.domain.repository.SurveyTypeInfo
import com.tinhcd.esalessfa.domain.repository.VisitRepository
import com.tinhcd.esalessfa.domain.repository.VisitGate
import com.tinhcd.esalessfa.feature.order.OrderEditViewModel
import com.tinhcd.esalessfa.feature.order.ProductPickerFragment
import com.tinhcd.esalessfa.feature.survey.SurveyFormViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tinhcd.esalessfa.feature.inventory.StockCountViewModel
import com.tinhcd.esalessfa.feature.visit.CheckInViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val repository: CustomerRepository,
    private val surveyRepository: SurveyRepository,
    private val visitRepository: VisitRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val customerId: String = savedStateHandle[CustomerDetailFragment.ARG_CUSTOMER_ID] ?: ""

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    private val _surveyTypes = MutableStateFlow<List<SurveyTypeInfo>>(emptyList())
    val surveyTypes: StateFlow<List<SurveyTypeInfo>> = _surveyTypes.asStateFlow()

    /**
     * Cổng nghiệp vụ tại cửa hàng này.
     *
     * Hai quy tắc Sales Step gộp lại: chỉ thao tác được khi đã check-in tại đúng
     * cửa hàng này, và mỗi thời điểm chỉ ghé được MỘT cửa hàng. Đang dở ở nơi
     * khác thì ngay cả nút check-in ở đây cũng phải khoá, nếu không nhân viên sẽ
     * mở hai lượt ghé chồng nhau và không lượt nào có giờ ra đúng.
     */
    val gate: StateFlow<VisitGate> = visitRepository.observeActiveVisit()
        .map { active ->
            when {
                active == null -> VisitGate.CanCheckIn
                active.customerId == customerId -> VisitGate.CheckedInHere(active)
                else -> VisitGate.BlockedByOther(active)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VisitGate.CanCheckIn)

    init {
        viewModelScope.launch {
            _customer.value = repository.getById(customerId)
            _surveyTypes.value = surveyRepository.types()
        }
    }
}

@AndroidEntryPoint
class CustomerDetailFragment : Fragment(R.layout.fragment_customer_detail) {

    private val viewModel: CustomerDetailViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerDetailBinding.bind(view)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Ba nút nghiệp vụ chưa có màn hình đích. Hiện rõ "sắp có" thay vì để nút
        // bấm không phản hồi — người dùng thử app sẽ tưởng bị treo.
        val notReady = View.OnClickListener {
            Snackbar.make(view, R.string.feature_coming_soon, Snackbar.LENGTH_SHORT).show()
        }
        binding.stockButton.setOnClickListener {
            val customer = viewModel.customer.value ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_customerDetail_to_stockCount,
                bundleOf(StockCountViewModel.ARG_CUSTOMER_ID to customer.id),
            )
        }

        binding.surveyButton.setOnClickListener {
            val customer = viewModel.customer.value ?: return@setOnClickListener
            val types = viewModel.surveyTypes.value

            when (types.size) {
                0 -> Snackbar.make(view, R.string.survey_no_type, Snackbar.LENGTH_SHORT).show()
                // Một loại duy nhất thì vào thẳng, đừng bắt chọn giữa một lựa chọn.
                1 -> openSurvey(customer.id, types.first().id)
                else -> MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.survey_pick_type)
                    .setItems(types.map { it.name }.toTypedArray()) { _, index ->
                        openSurvey(customer.id, types[index].id)
                    }
                    .show()
            }
        }

        binding.checkInButton.setOnClickListener {
            val customer = viewModel.customer.value ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_customerDetail_to_checkIn,
                bundleOf(CheckInViewModel.ARG_CUSTOMER_ID to customer.id),
            )
        }

        binding.orderButton.setOnClickListener {
            val customer = viewModel.customer.value ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_customerDetail_to_orderEdit,
                bundleOf(OrderEditViewModel.ARG_CUSTOMER_ID to customer.id),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val customer = viewModel.customer.filterNotNull().first()

            binding.toolbar.title = customer.name
            binding.code.text = customer.code
            binding.address.text = customer.address ?: getString(R.string.value_empty)
            binding.phone.text = customer.phone ?: getString(R.string.value_empty)

            binding.creditLimit.text = money.format(customer.creditLimit)
            binding.debtAmount.text = money.format(customer.debtAmount)
            binding.availableCredit.text = money.format(customer.availableCredit)

            binding.location.text = customer.location
                ?.let { getString(R.string.customer_coords, it.latitude, it.longitude) }
                ?: getString(R.string.customer_no_coords)
        }

        // Toàn bộ trạng thái bật/tắt của bốn nút do DUY NHẤT khối này quyết định.
        //
        // Trước đó có thêm một khối chạy-một-lần cũng gán checkInButton.isEnabled;
        // hai khối chạy bất đồng bộ nên khối kia có thể ghi đè sau và mở lại nút
        // đã bị khoá. Gom về một nơi thì không còn cửa cho lỗi đó.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.gate, viewModel.customer) { gate, customer ->
                    gate to customer
                }.collect { (gate, customer) ->
                    val canAct = gate.canDoBusiness()
                    binding.stockButton.isEnabled = canAct
                    binding.surveyButton.isEnabled = canAct
                    binding.orderButton.isEnabled = canAct

                    // Chỉ mở check-in khi CHƯA ghé đâu cả: đang ghé chính đây thì
                    // phải check-out, đang ghé nơi khác thì phải đi giải quyết ở đó.
                    // Ngoài ra khách hàng phải có toạ độ mới xác thực được.
                    binding.checkInButton.isEnabled =
                        gate.canCheckIn() && (customer?.canValidateCheckIn ?: false)

                    when (gate) {
                        is VisitGate.CheckedInHere -> {
                            // Đang ghé đây: nhắc phải check-out để chốt lượt ghé.
                            binding.stepHint.visibility = View.VISIBLE
                            binding.stepHint.setBackgroundResource(R.drawable.bg_chip_green)
                            binding.stepHint.setTextColor(
                                requireContext().getColor(R.color.stateGreen)
                            )
                            binding.stepHint.setText(R.string.step_checked_in_here)
                        }

                        VisitGate.CanCheckIn -> {
                            binding.stepHint.visibility = View.VISIBLE
                            binding.stepHint.setBackgroundResource(R.drawable.bg_chip_red)
                            binding.stepHint.setTextColor(
                                requireContext().getColor(R.color.brandRed)
                            )
                            binding.stepHint.setText(R.string.step_need_checkin)
                        }

                        is VisitGate.BlockedByOther -> {
                            binding.stepHint.visibility = View.VISIBLE
                            binding.stepHint.setBackgroundResource(R.drawable.bg_chip_orange)
                            binding.stepHint.setTextColor(
                                requireContext().getColor(R.color.stateOrange)
                            )
                            binding.stepHint.text = getString(
                                R.string.step_blocked_by_other,
                                gate.visit.customerName,
                            )
                        }
                    }
                }
            }
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

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
