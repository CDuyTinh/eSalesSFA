package com.tinhcd.esalessfa.feature.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.tinhcd.esalessfa.R
import com.tinhcd.esalessfa.databinding.FragmentDashboardBinding
import com.tinhcd.esalessfa.databinding.ItemDashboardKpiBinding
import com.tinhcd.esalessfa.databinding.ItemDashboardRankBinding
import com.tinhcd.esalessfa.domain.model.report.RankedItem
import com.tinhcd.esalessfa.domain.usecase.DayMetric
import com.tinhcd.esalessfa.domain.usecase.MonthKpi
import com.tinhcd.esalessfa.domain.usecase.MonthKpiType
import com.tinhcd.esalessfa.domain.usecase.RevenueRange
import com.tinhcd.esalessfa.domain.usecase.RevenueSeries
import com.tinhcd.esalessfa.feature.common.BarEntry
import com.tinhcd.esalessfa.feature.common.formatQty
import com.tinhcd.esalessfa.feature.common.isEmbedded
import com.tinhcd.esalessfa.feature.common.padTopForStatusBar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()

    private val money: NumberFormat = NumberFormat.getInstance(Locale("vi", "VN"))

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDashboardBinding.bind(view)

        view.padTopForStatusBar()

        // Làm tab thì không có gì để quay lại, và báo cáo đơn hàng đã là tab kế
        // bên nên không cần nút mở nữa.
        if (isEmbedded) {
            binding.toolbar.navigationIcon = null
            binding.reportButton.visibility = View.GONE
        } else {
            binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            binding.reportButton.setOnClickListener {
                findNavController().navigate(R.id.action_dashboard_to_orderReport)
            }
        }

        binding.chart.valueFormatter = { money.format(it) }

        binding.rangeGroup.check(R.id.rangeThisWeek)
        binding.rangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onRangeSelected(
                when (checkedId) {
                    R.id.rangeLastWeek -> RevenueRange.LAST_WEEK
                    R.id.rangeThisMonth -> RevenueRange.THIS_MONTH
                    else -> RevenueRange.THIS_WEEK
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val day = state.snapshot.day
                        binding.todayRevenue.text = money.format(day.revenue)
                        binding.todayOrders.text = day.orders.format()
                        binding.todaySkuPerOrder.text = day.skuPerOrder.format()
                        binding.todayVisits.text = day.visits.format()

                        binding.monthRevenue.text = money.format(state.snapshot.monthRevenue)
                        binding.kpiContainer.bindKpis(state.snapshot.monthKpis)

                        binding.topProductsContainer
                            .bindRanks(state.topProducts, R.string.dashboard_rank_qty)
                        binding.topCustomersContainer
                            .bindRanks(state.topCustomers, R.string.dashboard_rank_orders)
                    }
                }
                launch {
                    viewModel.revenueSeries.collect { series ->
                        binding.bindSeries(series)
                    }
                }
            }
        }
    }

    private fun FragmentDashboardBinding.bindSeries(series: RevenueSeries) {
        rangeTotal.text = money.format(series.total)
        rangeDates.text = getString(
            R.string.dashboard_range_dates,
            series.fromDate.format(DATE_LABEL),
            series.toDate.format(DATE_LABEL),
        )
        chart.setEntries(
            value = series.points.map { BarEntry(it.label, it.amount, it.isToday) },
            average = series.averagePerDay,
        )
    }

    /**
     * Số đạt được, kèm chỉ tiêu nếu có.
     *
     * SKU/Đơn hàng là số lẻ nên hiển thị một chữ số thập phân; đơn hàng và viếng
     * thăm luôn là số nguyên, ghi "4,0/32" sẽ rất khó chịu.
     */
    private fun DayMetric.format(): String {
        val actualText = if (target == null) {
            String.format(Locale("vi", "VN"), "%.1f", actual)
        } else {
            actual.toInt().toString()
        }
        return if (target == null) {
            actualText
        } else {
            getString(R.string.dashboard_metric_target, actualText, target)
        }
    }

    /**
     * Dựng lại toàn bộ dòng KPI mỗi lần state đổi.
     *
     * Danh sách chỉ có vài dòng và chỉ đổi khi sync xong, nên dựng lại đơn giản
     * hơn hẳn việc nuôi một adapter chỉ để tránh vài lần inflate.
     */
    private fun ViewGroup.bindKpis(kpis: List<MonthKpi>) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)

        kpis.forEach { kpi ->
            val row = ItemDashboardKpiBinding.inflate(inflater, this, false)
            val color = ContextCompat.getColor(context, kpi.type.color())

            row.donut.setRatio(kpi.ratio.toFloat())
            row.donut.setColor(color)
            row.kpiTitle.setText(kpi.type.title())
            row.kpiValue.text = kpi.type.valueText(kpi)
            row.kpiValue.setTextColor(color)

            addView(row.root)
        }
    }

    /**
     * @param qtyLabel chuỗi cho dòng phụ; sản phẩm đếm theo sản lượng còn khách
     *   hàng đếm theo số đơn, nên hai bảng dùng hai mẫu chữ khác nhau.
     */
    private fun ViewGroup.bindRanks(items: List<RankedItem>, @StringRes qtyLabel: Int) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)

        if (items.isEmpty()) {
            val empty = ItemDashboardRankBinding.inflate(inflater, this, false)
            empty.rankBadge.visibility = View.INVISIBLE
            empty.rankName.setText(R.string.report_no_data)
            empty.rankAmount.text = ""
            empty.rankQty.visibility = View.GONE
            empty.rankBar.visibility = View.GONE
            addView(empty.root)
            return
        }

        // So với hạng nhất chứ không so với tổng: chênh lệch giữa các hạng nhìn
        // rõ hơn, và hạng nhất luôn đầy thanh nên mắt có mốc để so.
        val top = items.maxOf { it.amount }.coerceAtLeast(1L)

        items.forEachIndexed { index, item ->
            val row = ItemDashboardRankBinding.inflate(inflater, this, false)
            val color = ContextCompat.getColor(context, rankColor(index))

            row.rankBadge.text = (index + 1).toString()
            row.rankBadge.backgroundTintList = ColorStateList.valueOf(color)
            row.rankName.text = item.name
            row.rankAmount.text = money.format(item.amount)
            row.rankQty.text = getString(qtyLabel, formatQty(item.qty))
            row.rankBar.progress = (item.amount * 100 / top).toInt()
            row.rankBar.setIndicatorColor(color)

            addView(row.root)
        }
    }

    /** Ba hạng đầu mang màu thương hiệu; từ hạng tư trở đi để xám cho lặng bớt. */
    @ColorRes
    private fun rankColor(index: Int): Int = when (index) {
        0 -> R.color.brandRed
        1 -> R.color.stateOrange
        2 -> R.color.stateBlue
        else -> R.color.textGray
    }

    @StringRes
    private fun MonthKpiType.title(): Int = when (this) {
        MonthKpiType.VISIT_COVERAGE -> R.string.dashboard_kpi_visit_coverage
        MonthKpiType.PRODUCTIVE_CALL -> R.string.dashboard_kpi_productive_call
        MonthKpiType.CUSTOMER_COVERAGE -> R.string.dashboard_kpi_customer_coverage
        MonthKpiType.SKU_PER_ORDER -> R.string.dashboard_kpi_sku_per_order
    }

    @ColorRes
    private fun MonthKpiType.color(): Int = when (this) {
        MonthKpiType.VISIT_COVERAGE -> R.color.stateBlue
        MonthKpiType.PRODUCTIVE_CALL -> R.color.stateGreen
        MonthKpiType.CUSTOMER_COVERAGE -> R.color.brandRed
        MonthKpiType.SKU_PER_ORDER -> R.color.stateOrange
    }

    /**
     * SKU/Đơn hàng là số lẻ; ba chỉ số còn lại đếm được nên để nguyên số nguyên.
     *
     * Chưa có mẫu số thì chỉ hiện số đạt được: "350/0" vừa sai vừa khiến người
     * đọc tưởng chỉ tiêu bằng 0, trong khi thực ra là chưa ghé lần nào.
     */
    private fun MonthKpiType.valueText(kpi: MonthKpi): String {
        val vn = Locale("vi", "VN")
        val decimals = if (this == MonthKpiType.SKU_PER_ORDER) 1 else 0
        val actualText = String.format(vn, "%.${decimals}f", kpi.actual)

        return if (kpi.target <= 0.0) {
            actualText
        } else {
            "$actualText/${String.format(vn, "%.0f", kpi.target)}"
        }
    }

    private companion object {
        val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
