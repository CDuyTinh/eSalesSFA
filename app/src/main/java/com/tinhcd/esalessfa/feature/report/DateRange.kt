package com.tinhcd.esalessfa.feature.report

import android.content.Context
import com.tinhcd.esalessfa.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Kỳ báo cáo đang xem, hai đầu đều là ngày ISO và đều nằm trong kỳ. */
data class DateRange(val from: String, val to: String)

private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Kỳ đang xem, dạng "01/08/2026 → 31/08/2026". */
internal fun DateRange.label(context: Context): String = context.getString(
    R.string.report_range,
    LocalDate.parse(from).format(DATE_LABEL),
    LocalDate.parse(to).format(DATE_LABEL),
)
