package com.tinhcd.esalessfa.domain.usecase

import com.tinhcd.esalessfa.domain.model.report.OrderCsv
import com.tinhcd.esalessfa.domain.model.report.OrderSummary
import com.tinhcd.esalessfa.domain.repository.ReportFileStore
import javax.inject.Inject

/**
 * Xuất báo cáo đơn hàng ra file CSV, trả về đường dẫn để chia sẻ.
 *
 * Ghép quy tắc định dạng ([OrderCsv]) với nơi ghi file ([ReportFileStore]) —
 * ViewModel không phải biết file tên gì và nằm ở đâu, nó chỉ đưa danh sách đơn.
 */
class ExportOrderReportUseCase @Inject constructor(
    private val reportFileStore: ReportFileStore,
) {

    suspend operator fun invoke(orders: List<OrderSummary>, date: String): String =
        reportFileStore.write(
            fileName = OrderCsv.fileName(date),
            content = OrderCsv.build(orders),
        )
}
