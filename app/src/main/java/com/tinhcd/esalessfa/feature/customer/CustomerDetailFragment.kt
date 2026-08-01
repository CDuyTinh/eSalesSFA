package com.tinhcd.esalessfa.feature.customer

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import com.google.android.material.button.MaterialButton
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentCustomerDetailBinding
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.feature.common.roundBottomCorners
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chi tiết khách hàng, theo CustomerInforActivity của bản eSales gốc: ảnh cửa
 * hàng co giãn ở trên, dưới là ba tab Công việc / Thông tin / Chương trình.
 *
 * Tab "Công việc" chỉ có mặt khi đang check-in TẠI cửa hàng này — đúng quy tắc
 * isAllowSales của bản gốc: có lượt ghé đang mở VÀ lượt đó thuộc chính khách
 * hàng đang xem. Đang ghé nơi khác, hoặc đã check-out, thì màn này chỉ để xem.
 */
@AndroidEntryPoint
class CustomerDetailFragment : Fragment(R.layout.fragment_customer_detail) {

    private val viewModel: CustomerDetailViewModel by viewModels()

    /** Nhãn của từng tab nằm trong layout; ở đây chỉ cần biết đang ở tab nào. */
    private enum class Tab { WORK, INFO, PROGRAM }

    /** Tab đang xem, để giữ nguyên chỗ đang đứng khi hàng chip đổi. */
    private var currentTab: Tab? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerDetailBinding.bind(view)

        // Đệm ở AppBarLayout chứ không ở view gốc: nền đỏ của appbar nhờ vậy trải
        // luôn lên sau status bar, còn nội dung vẫn nằm dưới đồng hồ. Đệm view
        // gốc sẽ để lộ một dải nền sáng phía trên phần đầu đỏ.
        binding.appBar.padTopForStatusBar()
        binding.photoHeader.roundBottomCorners(resources.getDimension(R.dimen.photo_header_radius))
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Đặt lại trạng thái sau khi bấm: MaterialButton tự lật checked mỗi lần
        // chạm, bấm lại chip đang chọn sẽ làm cả ba chip cùng tắt.
        binding.chips().forEach { (tab, chip) ->
            chip.setOnClickListener { selectTab(binding, tab) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.customer.filterNotNull().collect { customer ->
                        binding.name.text = customer.name
                        binding.code.text = customer.code
                        binding.photoInitial.text = customer.name.trim().take(1).uppercase()

                        // Ảnh đè lên chữ cái đầu: khách chưa có ảnh thì thấy chữ,
                        // ảnh đang tải cũng không để lại ô trống.
                        if (customer.imageUrl.isNullOrBlank()) {
                            binding.photo.visibility = View.GONE
                        } else {
                            binding.photo.visibility = View.VISIBLE
                            binding.photo.load(customer.imageUrl) { crossfade(true) }
                        }
                    }
                }
                launch {
                    // Chỉ động vào hàng chip khi quyền thao tác THỰC SỰ đổi:
                    // trạng thái ghé thăm phát lại liên tục, mà mỗi lần chọn lại
                    // tab là một lần fragment con có thể bị dựng lại.
                    viewModel.gate
                        .map { it.canDoBusiness() }
                        .distinctUntilChanged()
                        .collect { canDoBusiness -> bindTabs(binding, canDoBusiness) }
                }
            }
        }
    }

    private fun bindTabs(binding: FragmentCustomerDetailBinding, canDoBusiness: Boolean) {
        binding.tabWork.isVisible = canDoBusiness

        // Mở màn lúc đang được thao tác thì vào thẳng tab Công việc, giống bản
        // gốc đặt selectedIndexTab = 0 khi tạo tab đó. Còn tab Công việc biến
        // mất do check-out giữa chừng thì lùi về Thông tin; các trường hợp còn
        // lại giữ nguyên tab đang xem để không kéo người dùng đi chỗ khác.
        val current = currentTab
        val target = when {
            current == null -> if (canDoBusiness) Tab.WORK else Tab.INFO
            current == Tab.WORK && !canDoBusiness -> Tab.INFO
            else -> current
        }
        selectTab(binding, target)
    }

    private fun selectTab(binding: FragmentCustomerDetailBinding, tab: Tab) {
        currentTab = tab
        binding.chips().forEach { (chipTab, chip) -> chip.isChecked = chipTab == tab }
        showTab(tab)
    }

    /** Ba chip theo đúng thứ tự bày trên màn hình. */
    private fun FragmentCustomerDetailBinding.chips(): List<Pair<Tab, MaterialButton>> =
        listOf(Tab.WORK to tabWork, Tab.INFO to tabInfo, Tab.PROGRAM to tabProgram)

    /**
     * Thay nội dung tab bằng FragmentManager, không dùng ViewPager.
     *
     * Số tab thay đổi lúc chạy; với ViewPager2 thì mỗi lần đổi phải dựng lại cả
     * adapter, còn ở đây chỉ là một lần replace. Màn Home cũng đang đổi tab theo
     * cách này nên hai chỗ đọc giống nhau.
     */
    private fun showTab(tab: Tab) {
        val tag = tab.name
        if (childFragmentManager.findFragmentByTag(tag)?.isVisible == true) return

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.tabContainer, fragmentFor(tab), tag)
        }
    }

    private fun fragmentFor(tab: Tab): Fragment = when (tab) {
        Tab.WORK -> CustomerWorkTabFragment()
        Tab.INFO -> CustomerInfoTabFragment()
        Tab.PROGRAM -> CustomerProgramTabFragment()
    }

    companion object {
        const val ARG_CUSTOMER_ID = "customerId"
    }
}
