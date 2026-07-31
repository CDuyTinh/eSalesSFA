package com.tinhcd.esalessfa.feature.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.SheetCustomerMapInfoBinding
import com.tinhcd.esalessfa.domain.model.RouteCustomer
import com.tinhcd.esalessfa.domain.model.VisitState

/**
 * Thông tin khách hàng hiện lên khi chạm vào ghim, theo
 * PrepareMapMarkerInfoDialog của bản eSales gốc.
 *
 * Dữ liệu truyền qua arguments dạng chuỗi/số chứ không giữ tham chiếu tới đối
 * tượng: hệ thống dựng lại dialog sau khi xoay máy hay thu hồi tiến trình, lúc
 * đó mọi thuộc tính đặt bằng tay đều mất.
 */
class CustomerMapInfoSheet : BottomSheetDialogFragment() {

    private var binding: SheetCustomerMapInfoBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = SheetCustomerMapInfoBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding ?: return
        val args = requireArguments()

        val customerId = args.getString(ARG_ID).orEmpty()
        val address = args.getString(ARG_ADDRESS).orEmpty()

        binding.sheetCode.text = args.getString(ARG_CODE)
        binding.sheetName.text = args.getString(ARG_NAME)

        binding.sheetAddressRow.visibility = if (address.isBlank()) View.GONE else View.VISIBLE
        binding.sheetAddress.text = address

        binding.sheetCoordinates.text = if (args.containsKey(ARG_LAT)) {
            getString(
                R.string.customer_coords,
                args.getDouble(ARG_LAT),
                args.getDouble(ARG_LNG),
            )
        } else {
            getString(R.string.customer_map_no_coordinates)
        }

        // Nhãn mã đổi màu theo trạng thái ghé, cùng bộ màu với danh sách và ghim.
        binding.sheetCode.setBackgroundResource(
            when (args.getString(ARG_STATE)) {
                VisitState.IN_PROGRESS.name -> R.drawable.bg_badge_blue
                VisitState.DONE.name -> R.drawable.bg_badge_green
                else -> R.drawable.bg_badge_red
            }
        )

        binding.sheetOpenButton.setOnClickListener {
            // Trả kết quả về màn bản đồ thay vì tự điều hướng: điều hướng là việc
            // của màn hình chủ, dialog chỉ báo người dùng đã chọn gì.
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CUSTOMER_ID to customerId))
            dismiss()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST_KEY = "customer_map_info"
        const val RESULT_CUSTOMER_ID = "customerId"

        private const val ARG_ID = "id"
        private const val ARG_CODE = "code"
        private const val ARG_NAME = "name"
        private const val ARG_ADDRESS = "address"
        private const val ARG_LAT = "lat"
        private const val ARG_LNG = "lng"
        private const val ARG_STATE = "state"

        fun newInstance(item: RouteCustomer) = CustomerMapInfoSheet().apply {
            val customer = item.customer
            arguments = bundleOf(
                ARG_ID to customer.id,
                ARG_CODE to customer.code,
                ARG_NAME to customer.name,
                ARG_ADDRESS to customer.address.orEmpty(),
                ARG_STATE to item.visitState.name,
            ).apply {
                customer.location?.let {
                    putDouble(ARG_LAT, it.latitude)
                    putDouble(ARG_LNG, it.longitude)
                }
            }
        }
    }
}
