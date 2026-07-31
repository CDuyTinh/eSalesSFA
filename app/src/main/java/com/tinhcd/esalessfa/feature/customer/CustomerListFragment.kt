package com.tinhcd.esalessfa.feature.customer

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.feature.common.isEmbedded
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import com.tinhcd.esalessfa.databinding.FragmentCustomerListBinding
import com.tinhcd.esalessfa.feature.visit.CheckInViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomerListFragment : Fragment(R.layout.fragment_customer_list) {

    private val viewModel: CustomerListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerListBinding.bind(view)

        view.padTopForStatusBar()

        // Điều hướng theo ID ĐÍCH thay vì ID action: fragment này chạy ở hai chỗ —
        // vừa là tab Check-in trong màn Home, vừa là đích riêng cho "Tất cả khách
        // hàng". Action chỉ hợp lệ khi đang đứng đúng đích khai nó, nên ở trong
        // tab thì action của customerListFragment không giải được.
        val openDetail: (String) -> Unit = { customerId ->
            findNavController().navigate(
                R.id.customerDetailFragment,
                bundleOf(CustomerDetailFragment.ARG_CUSTOMER_ID to customerId),
            )
        }

        // Làm tab thì không có gì để quay lại — navigateUp() sẽ pop cả màn Home.
        if (isEmbedded) binding.toolbar.navigationIcon = null
        else binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.toolbar.setTitle(
            if (viewModel.mode == CustomerListMode.ROUTE_TODAY) R.string.action_check_in
            else R.string.customer_all
        )

        // Hàng tiêu đề ghi rõ đang xem tuyến của thứ mấy; danh sách toàn bộ khách
        // hàng thì không thuộc tuyến nào nên dùng luôn tên màn hình.
        binding.routeTitle.text = if (viewModel.mode == CustomerListMode.ROUTE_TODAY) {
            val weekdays = resources.getStringArray(R.array.weekday_names)
            getString(R.string.customer_route_title, weekdays[viewModel.dayOfWeek - 1])
        } else {
            getString(R.string.customer_all)
        }

        // Không dùng DividerItemDecoration nữa: mỗi khách hàng đã là một thẻ
        // riêng có khoảng cách, thêm đường kẻ giữa các thẻ sẽ thành thừa.
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Ô tìm kiếm chỉ set text một lần: gán lại mỗi khi state đổi sẽ đẩy con trỏ
        // về đầu dòng trong lúc user đang gõ.
        binding.searchInput.setText(viewModel.currentQuery.value)
        binding.searchInput.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }

        binding.mapButton.visibility =
            if (viewModel.mode == CustomerListMode.ROUTE_TODAY) View.VISIBLE else View.GONE
        binding.mapButton.setOnClickListener {
            findNavController().navigate(R.id.customerMapFragment)
        }

        when (viewModel.mode) {
            CustomerListMode.ROUTE_TODAY -> bindRoute(binding, openDetail)
            CustomerListMode.ALL -> bindAll(binding, openDetail)
        }
    }

    private fun bindRoute(
        binding: FragmentCustomerListBinding,
        onClick: (String) -> Unit,
    ) {
        // Nút trên thẻ mở thẳng màn viếng thăm, không vòng qua màn chi tiết: đó
        // là thao tác nhân viên làm nhiều nhất khi đứng trước cửa hàng.
        val adapter = RouteCustomerAdapter(onClick = onClick, onCheckIn = ::openVisit)
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todayRoute.collect { route ->
                    adapter.assignedTo = route.assignedTo
                    adapter.submitList(route.customers)
                    binding.countText.text =
                        getString(R.string.customer_count, route.customers.size)
                    binding.emptyText.visibility =
                        if (route.customers.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun openVisit(customerId: String) {
        findNavController().navigate(
            R.id.checkInFragment,
            bundleOf(CheckInViewModel.ARG_CUSTOMER_ID to customerId),
        )
    }

    private fun bindAll(
        binding: FragmentCustomerListBinding,
        onClick: (String) -> Unit,
    ) {
        val adapter = CustomerPagingAdapter(onClick)
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedCustomers.collect { adapter.submitData(it) }
                }
                launch {
                    // itemCount của Paging chỉ phản ánh số dòng ĐÃ tải, nên hiển thị
                    // sau mỗi lần adapter cập nhật thay vì đếm tổng.
                    adapter.loadStateFlow.collect {
                        binding.countText.text =
                            getString(R.string.customer_count_loaded, adapter.itemCount)
                        binding.emptyText.visibility =
                            if (adapter.itemCount == 0) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
