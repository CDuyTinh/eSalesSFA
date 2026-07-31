package com.tinhcd.esalessfa.feature.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/** Một cột trong biểu đồ. */
data class BarEntry(val label: String, val value: Long)

/**
 * Biểu đồ cột tối giản, vẽ bằng Canvas.
 *
 * Tự vẽ thay vì kéo thêm thư viện chart: cả app chỉ cần đúng một loại biểu đồ,
 * và một thư viện chart thường nặng vài trăm KB cùng một kho maven riêng. Chưa
 * kể khi chuyển UI sang Compose sau này, logic vẽ ở đây chuyển thẳng sang
 * Canvas của Compose gần như nguyên vẹn.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var entries: List<BarEntry> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val barRect = RectF()

    init {
        barPaint.color = resolveThemeColor(androidx.appcompat.R.attr.colorPrimary)
        gridPaint.color = ContextCompat.getColor(context, android.R.color.darker_gray)
        gridPaint.alpha = 60
        labelPaint.color = ContextCompat.getColor(context, android.R.color.darker_gray)
    }

    fun setEntries(value: List<BarEntry>) {
        entries = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val labelHeight = 34f
        val chartHeight = height - labelHeight
        // Không chia cho 0 khi cả kỳ chưa có đơn nào.
        val maxValue = max(entries.maxOf { it.value }, 1L).toFloat()

        // Ba đường lưới ngang để mắt ước lượng được độ lớn mà không cần trục số.
        for (i in 1..3) {
            val y = chartHeight * i / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        val slot = width.toFloat() / entries.size
        val barWidth = slot * 0.6f
        val gap = (slot - barWidth) / 2f

        entries.forEachIndexed { index, entry ->
            val ratio = entry.value / maxValue
            val barHeight = chartHeight * ratio
            val left = index * slot + gap

            barRect.set(left, chartHeight - barHeight, left + barWidth, chartHeight)
            // Cột giá trị 0 vẫn vẽ một vạch mỏng để thấy rõ "ngày đó không bán
            // được gì", khác với "không có dữ liệu".
            if (entry.value == 0L) {
                barRect.top = chartHeight - 2f
                barPaint.alpha = 60
            } else {
                barPaint.alpha = 255
            }
            canvas.drawRoundRect(barRect, 6f, 6f, barPaint)

            // Nhiều cột thì chỉ ghi nhãn cách quãng để chữ không chồng lên nhau.
            val labelStep = if (entries.size > 10) entries.size / 5 else 1
            if (index % labelStep == 0) {
                canvas.drawText(
                    entry.label,
                    left + barWidth / 2f,
                    height - 6f,
                    labelPaint,
                )
            }
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typed = context.obtainStyledAttributes(intArrayOf(attr))
        val color = typed.getColor(0, 0xFF6750A4.toInt())
        typed.recycle()
        return color
    }
}
