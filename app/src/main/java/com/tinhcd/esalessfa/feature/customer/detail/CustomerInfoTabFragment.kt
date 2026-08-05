package com.tinhcd.esalessfa.feature.customer.detail

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentCustomerInfoTabBinding
import com.tinhcd.esalessfa.domain.model.visit.VisitGate
import com.tinhcd.esalessfa.feature.visit.CheckInViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Tab "Thông tin": hồ sơ, công nợ và cửa vào lượt ghé.
 *
 * Dùng chung ViewModel với màn chủ qua ownerProducer: dữ liệu khách hàng đã nạp
 * một lần ở đó, tab không việc gì phải hỏi lại kho.
 */
@AndroidEntryPoint
class CustomerInfoTabFragment : Fragment(R.layout.fragment_customer_info_tab) {

    private val viewModel: CustomerDetailViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerInfoTabBinding.bind(view)
        val money = NumberFormat.getInstance(Locale("vi", "VN"))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.customer, viewModel.gate) { customer, gate ->
                    customer to gate
                }.collect { (customer, gate) ->
                    if (customer == null) return@collect

                    binding.code.text = customer.code
                    binding.channel.text = customer.channelName ?: getString(R.string.value_empty)
                    binding.address.text = customer.address ?: getString(R.string.value_empty)
                    binding.phone.text = customer.phone ?: getString(R.string.value_empty)
                    binding.location.text = customer.location
                        ?.let { getString(R.string.customer_coords, it.latitude, it.longitude) }
                        ?: getString(R.string.customer_no_coords)

                    binding.creditLimit.text = money.format(customer.creditLimit)
                    binding.debtAmount.text = money.format(customer.debtAmount)
                    binding.availableCredit.text = money.format(customer.availableCredit)

                    bindGate(binding, gate, customer.canValidateCheckIn, customer.id)
                }
            }
        }
    }

    private fun bindGate(
        binding: FragmentCustomerInfoTabBinding,
        gate: VisitGate,
        hasLocation: Boolean,
        customerId: String,
    ) {
        // Nút này là CỬA VÀO màn viếng thăm, dùng cho cả check-in lẫn check-out.
        // Chỉ khoá khi đang ghé cửa hàng khác; đang ghé chính đây thì phải vào
        // được, vì đó là đường duy nhất để check-out.
        //
        // Toạ độ chỉ cần cho check-in — check-out không xác thực bán kính nên
        // khách thiếu toạ độ vẫn phải đóng được lượt ghé.
        binding.checkInButton.isEnabled = gate.canOpenVisitScreen() &&
            (!gate.requiresCustomerLocation() || hasLocation)

        binding.checkInButton.setText(
            if (gate.canDoBusiness()) R.string.action_check_out else R.string.action_check_in
        )
        binding.checkInButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_customerDetail_to_checkIn,
                bundleOf(CheckInViewModel.ARG_CUSTOMER_ID to customerId),
            )
        }

        when (gate) {
            is VisitGate.CheckedInHere -> {
                binding.stepHint.setText(R.string.step_checked_in_here)
                binding.stepHint.setBackgroundResource(R.drawable.bg_chip_blue)
                binding.stepHint.setTextColor(requireContext().getColor(R.color.stateBlue))
            }

            is VisitGate.BlockedByOther -> {
                binding.stepHint.text =
                    getString(R.string.step_blocked_by_other, gate.visit.customerName)
                binding.stepHint.setBackgroundResource(R.drawable.bg_chip_orange)
                binding.stepHint.setTextColor(requireContext().getColor(R.color.stateOrange))
            }

            VisitGate.CanCheckIn -> {
                binding.stepHint.setText(R.string.step_need_checkin)
                binding.stepHint.setBackgroundResource(R.drawable.bg_chip_red)
                binding.stepHint.setTextColor(requireContext().getColor(R.color.brandRed))
            }
        }
    }
}
