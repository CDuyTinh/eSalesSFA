package com.tinhcd.esalessfa.domain.model.report

/**
 * Dựng nội dung file CSV báo cáo đơn hàng.
 *
 * Hàm thuần, không đụng file lẫn Android, nên viết test cho định dạng xuất ra
 * được mà không cần thiết bị. Trước đây đoạn này nằm trong ViewModel và phần ghi
 * file nằm trong Fragment, tức là quy tắc định dạng bị kẹt trong tầng giao diện.
 */
object OrderCsv {

    private const val SEPARATOR = ';'

    /**
     * Thêm BOM UTF-8 ở đầu file: thiếu nó thì Excel trên Windows đọc tiếng Việt
     * thành ký tự rác. Đây là lỗi rất hay gặp khi xuất báo cáo cho khách VN.
     */
    fun build(orders: List<OrderSummary>): String = buildString {
        append('﻿')
        appendLine("Ma don;Ngay;Khach hang;So dong;Thanh tien;Trang thai;Da dong bo")
        orders.forEach { o ->
            // Thay ; trong tên khách hàng để không vỡ cột.
            val name = o.customerName.replace(SEPARATOR, ',')
            appendLine(
                "${o.orderNo};${o.orderDate};$name;${o.lineCount};" +
                    "${o.totalAmount};${o.status};${if (o.isSynced) "Roi" else "Chua"}"
            )
        }
    }

    /** Tên file kèm ngày để nhiều lần xuất trong ngày ghi đè lên nhau, không rác máy. */
    fun fileName(date: String): String = "bao-cao-don-hang-$date.csv"
}
