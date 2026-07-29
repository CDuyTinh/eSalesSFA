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
import com.tinhcd.esalessfa.databinding.FragmentCustomerListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CustomerListFragment : Fragment(R.layout.fragment_customer_list) {

    private val viewModel: CustomerListViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentCustomerListBinding.bind(view)

        val openDetail: (String) -> Unit = { customerId ->
            findNavController().navigate(
                R.id.action_customerList_to_customerDetail,
                bundleOf(CustomerDetailFragment.ARG_CUSTOMER_ID to customerId),
            )
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setTitle(
            if (viewModel.mode == CustomerListMode.ROUTE_TODAY) R.string.home_route_today
            else R.string.customer_all
        )

        // Không dùng DividerItemDecoration nữa: mỗi khách hàng đã là một thẻ
        // riêng có khoảng cách, thêm đường kẻ giữa các thẻ sẽ thành thừa.
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Ô tìm kiếm chỉ set text một lần: gán lại mỗi khi state đổi sẽ đẩy con trỏ
        // về đầu dòng trong lúc user đang gõ.
        binding.searchInput.setText(viewModel.currentQuery.value)
        binding.searchInput.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
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
        val adapter = RouteCustomerAdapter(onClick)
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.routeCustomers.collect { list ->
                    adapter.submitList(list)
                    binding.countText.text =
                        getString(R.string.customer_count_format, list.size)
                    binding.emptyText.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
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
