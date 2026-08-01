package com.tinhcd.esalessfa.feature.report

import android.content.Context
import com.tinhcd.esalessfa.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cách hiển thị số và ngày dùng chung cho màn báo cáo.
 *
 * Bốn màn con cùng in một dạng tiền và một dạng kỳ; để mỗi màn tự dựng lại thì
 * chỉ cần sửa sót một chỗ là hai tab hiện hai kiểu số khác nhau.
 */
private val VN = Locale("vi", "VN")

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Tiền VND: nhóm hàng nghìn bằng dấu chấm, không phần lẻ. */
internal fun moneyFormat(): NumberFormat = NumberFormat.getInstance(VN)

/** Kỳ đang xem, dạng "01/08/2026 → 31/08/2026". */
internal fun DateRange.label(context: Context): String = context.getString(
    R.string.report_range,
    LocalDate.parse(from).format(DATE_LABEL),
    LocalDate.parse(to).format(DATE_LABEL),
)

/**
 * Ngày ISO trong kho đổi sang dạng người Việt đọc: "2026-07-29" → "29/07/2026".
 *
 * Chuỗi không phải ngày thì giữ nguyên — thà hiện đúng thứ đang lưu còn hơn
 * làm sập màn hình vì một dòng dữ liệu lỗi.
 */
internal fun String.asDateLabel(): String =
    runCatching { LocalDate.parse(this).format(DATE_LABEL) }.getOrDefault(this)

/** Sản lượng thường là số tròn; chỉ hiện phần lẻ khi thật sự có. */
internal fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) String.format(VN, "%,.0f", qty)
    else String.format(VN, "%,.1f", qty)
