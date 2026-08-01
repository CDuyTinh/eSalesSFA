package com.tinhcd.esalessfa.feature.report

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentOrderReportBinding
import com.tinhcd.esalessfa.feature.common.isEmbedded
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Màn báo cáo, theo ReportOrderTabFragment của bản eSales gốc: chọn kỳ ở đầu
 * màn rồi xem cùng kỳ đó dưới ba góc — Đơn hàng, Sản phẩm, Khách hàng.
 *
 * Màn này chỉ giữ phần khung: kỳ đang xem, hàng chip, và việc chia sẻ file đã
 * xuất. Số liệu của từng tab do fragment con đọc từ ViewModel dùng chung.
 */
@AndroidEntryPoint
class OrderReportFragment : Fragment(R.layout.fragment_order_report) {

    private val viewModel: OrderReportViewModel by viewModels()

    private enum class Tab { ORDER, PRODUCT, CUSTOMER }

    /** Tab đang xem, để dựng lại đúng chỗ cũ sau khi view bị tạo lại. */
    private var currentTab: Tab = Tab.ORDER

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentOrderReportBinding.bind(view)

        // Đệm ở khối đầu chứ không ở view gốc: nền đỏ nhờ vậy trải lên sau status
        // bar, còn toolbar vẫn nằm dưới đồng hồ.
        binding.header.padTopForStatusBar()

        // Làm tab thì không có gì để quay lại — navigateUp() sẽ pop cả màn Home.
        if (isEmbedded) binding.toolbar.navigationIcon = null
        else binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.rangeButton.setOnClickListener { showRangePicker() }

        // Đặt lại trạng thái sau khi bấm: MaterialButton tự lật checked mỗi lần
        // chạm, bấm lại chip đang chọn sẽ làm cả ba chip cùng tắt.
        binding.chips().forEach { (tab, chip) ->
            chip.setOnClickListener { selectTab(binding, tab) }
        }
        selectTab(binding, currentTab)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentRange.collect { range ->
                        binding.rangeButton.text = range.label(requireContext())
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is OrderReportEvent.Exported -> shareCsv(event.filePath)

                            OrderReportEvent.ExportFailed -> Snackbar.make(
                                view,
                                R.string.report_export_failed,
                                Snackbar.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun selectTab(binding: FragmentOrderReportBinding, tab: Tab) {
        currentTab = tab
        binding.chips().forEach { (chipTab, chip) -> chip.isChecked = chipTab == tab }
        showTab(tab)
    }

    /** Ba chip theo đúng thứ tự bày trên màn hình. */
    private fun FragmentOrderReportBinding.chips(): List<Pair<Tab, MaterialButton>> =
        listOf(Tab.ORDER to tabOrder, Tab.PRODUCT to tabProduct, Tab.CUSTOMER to tabCustomer)

    /**
     * Thay nội dung tab bằng FragmentManager, không dùng ViewPager — giống màn
     * chi tiết khách hàng, để hai màn có tab trong app đọc giống nhau.
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
        Tab.ORDER -> ReportOrderTabFragment()
        Tab.PRODUCT -> ReportProductTabFragment()
        Tab.CUSTOMER -> ReportCustomerTabFragment()
    }

    private fun showRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.report_pick_range)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            // MaterialDatePicker trả epoch millis theo UTC; đổi bằng múi giờ máy
            // sẽ lệch một ngày với người dùng ở UTC+7.
            val from = selection.first?.toLocalDate() ?: return@addOnPositiveButtonClickListener
            val to = selection.second?.toLocalDate() ?: from
            viewModel.setRange(from.toString(), to.toString())
        }
        picker.show(childFragmentManager, "range")
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    /**
     * Mở hộp thoại chia sẻ cho file ViewModel vừa ghi.
     *
     * FileProvider thay vì file:// — Android 7+ ném FileUriExposedException khi
     * chia sẻ đường dẫn file thô ra ngoài app.
     */
    private fun shareCsv(filePath: String) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            File(filePath),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.report_share)))
    }
}
