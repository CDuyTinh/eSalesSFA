package com.tinhcd.esalessfa.feature.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentHomeBinding
import com.tinhcd.esalessfa.feature.common.asTab
import com.tinhcd.esalessfa.feature.customer.list.CustomerListFragment
import com.tinhcd.esalessfa.feature.customer.list.CustomerListMode
import com.tinhcd.esalessfa.feature.customer.list.CustomerListViewModel
import com.tinhcd.esalessfa.feature.dashboard.DashboardFragment
import com.tinhcd.esalessfa.feature.report.OrderReportFragment
import com.tinhcd.esalessfa.feature.work.WorkFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Khung chứa bốn tab, theo HomeActivity của bản eSales gốc.
 *
 * Chỉ giữ thanh tab và vùng nội dung; mỗi tab là một fragment độc lập với
 * ViewModel riêng.
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null

    /**
     * Tab đang hiện, để không dựng lại tab đã có.
     *
     * Cần thiết vì đoạn khởi tạo bên dưới có thể gọi showTab hai lần cho cùng một
     * tab. commit() chạy bất đồng bộ nên lần gọi thứ hai sẽ không tìm thấy
     * fragment của lần đầu qua findFragmentByTag và sẽ thêm một bản thứ hai.
     */
    private var currentTabId = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view).also { this.binding = it }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }

        // Chỉ chọn tab mặc định khi CHƯA có tab nào, tức màn hình thật sự mới.
        //
        // Không dùng savedInstanceState để nhận biết điều đó. Đi từ tab vào màn
        // chi tiết rồi bấm Back, fragment này vẫn là instance cũ nằm trong back
        // stack: chỉ view bị huỷ rồi dựng lại nên savedInstanceState là null,
        // trong khi các tab con vẫn còn nguyên. Ép chọn tab đầu lúc đó là lý do
        // màn hình nhảy về Tổng quan còn thanh tab vẫn sáng ở tab cũ.
        if (childFragmentManager.fragments.isEmpty()) {
            binding.bottomNav.selectedItemId = R.id.tab_dashboard
            // Gọi thẳng thêm một lần: tab_dashboard là item đầu tiên của menu nên
            // BottomNavigationView coi như đã chọn sẵn, gán selectedItemId đúng
            // giá trị đó có thể không phát ra sự kiện nào và màn hình sẽ trắng.
            showTab(R.id.tab_dashboard)
        }
    }

    /**
     * Kéo nội dung về khớp với thanh tab sau khi view được khôi phục.
     *
     * BottomNavigationView tự khôi phục tab đang chọn ở bước NÀY, tức sau
     * onViewCreated, và nó khôi phục lặng lẽ chứ không phát sự kiện chọn tab.
     * Ai đặt tab ở onViewCreated cũng sẽ bị nó ghi đè, và đó chính là cảnh thanh
     * tab chỉ một đằng còn màn hình hiện một nẻo. Lấy luôn tab của thanh tab làm
     * chuẩn thì hai bên không thể lệch nhau nữa.
     */
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        binding?.let { showTab(it.bottomNav.selectedItemId) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    /** Cho tab khác chuyển tab, ví dụ nút "Xem tuyến hôm nay" ở tab Công việc. */
    fun selectTab(itemId: Int) {
        binding?.bottomNav?.selectedItemId = itemId
    }

    /**
     * Dùng add/hide/show chứ không replace.
     *
     * replace sẽ huỷ view của tab cũ, nên quay lại tab đó là mất vị trí cuộn, ô
     * tìm kiếm đang gõ và khoảng thời gian đang chọn ở báo cáo. Bản gốc cũng giữ
     * lại instance của từng tab đúng vì lý do này.
     */
    private fun showTab(itemId: Int) {
        if (itemId == currentTabId) return
        currentTabId = itemId

        val tag = itemId.toString()
        val fm = childFragmentManager

        fm.commit {
            setReorderingAllowed(true)
            fm.fragments.forEach { hide(it) }

            val existing = fm.findFragmentByTag(tag)
            if (existing == null) add(R.id.tabContainer, newTab(itemId), tag) else show(existing)
        }
    }

    private fun newTab(itemId: Int): Fragment = when (itemId) {
        R.id.tab_dashboard -> DashboardFragment().asTab()

        // Tab Check-in chính là tuyến hôm nay: danh sách các cửa hàng phải ghé.
        R.id.tab_checkin -> CustomerListFragment().asTab(
            CustomerListViewModel.ARG_MODE to CustomerListMode.ROUTE_TODAY.name
        )

        R.id.tab_report -> OrderReportFragment().asTab()
        else -> WorkFragment().asTab()
    }
}
