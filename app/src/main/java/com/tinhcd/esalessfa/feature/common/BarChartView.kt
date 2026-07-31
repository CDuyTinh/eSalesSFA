package com.tinhcd.esalessfa.feature.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.tinhcd.esalessfa.R
import kotlin.math.max

/** Một cột trong biểu đồ. */
data class BarEntry(
    val label: String,
    val value: Long,
    /** Cột của hôm nay: vẽ đậm, các cột còn lại mờ đi. */
    val isHighlighted: Boolean = false,
)

/**
 * Biểu đồ cột tối giản, vẽ bằng Canvas.
 *
 * Tự vẽ thay vì kéo thêm thư viện chart: cả app chỉ cần đúng một loại biểu đồ,
 * và một thư viện chart thường nặng vài trăm KB cùng một kho maven riêng. Chưa
 * kể khi chuyển UI sang Compose sau này, logic vẽ ở đây chuyển thẳng sang
 * Canvas của Compose gần như nguyên vẹn.
 *
 * Chạm vào một cột sẽ hiện bóng ghi ngày và số tiền. Không có nó thì biểu đồ chỉ
 * cho biết ngày nào cao hơn ngày nào, còn con số cụ thể của từng ngày thì không
 * đọc được ở đâu cả.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var entries: List<BarEntry> = emptyList()
    private var average: Long = 0
    private var selectedIndex: Int = NO_SELECTION

    /** Cách hiển thị số tiền trong bóng chú thích; màn hình quyết định định dạng. */
    var valueFormatter: (Long) -> String = { it.toString() }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(1f) }
    private val averagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val todayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val averageLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(9f)
        textAlign = Paint.Align.RIGHT
    }
    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val barRect = RectF()
    private val barPath = Path()
    private val tooltipRect = RectF()
    private val barRadii = FloatArray(8)

    private val barTopColor = context.getColor(R.color.brandRed)
    private val barBottomColor = context.getColor(R.color.brandRedLight)

    init {
        gridPaint.color = context.getColor(R.color.lineGray)
        gridPaint.alpha = GRID_ALPHA
        labelPaint.color = context.getColor(R.color.textGray)
        todayLabelPaint.color = barTopColor
        averagePaint.color = context.getColor(R.color.textGrayDark)
        averagePaint.alpha = AVERAGE_ALPHA
        averageLabelPaint.color = context.getColor(R.color.textGray)
        tooltipPaint.color = context.getColor(R.color.textDark)
        tooltipTextPaint.color = context.getColor(R.color.white)

        // Bo hai góc TRÊN của cột: bo cả bốn góc thì chân cột bị lượn, nhìn như
        // đang lơ lửng trên đường trục.
        val radius = dp(4f)
        barRadii[0] = radius
        barRadii[1] = radius
        barRadii[2] = radius
        barRadii[3] = radius
    }

    /**
     * @param average mức trung bình ngày để vẽ đường đứt đoạn; 0 thì bỏ đường đó.
     */
    fun setEntries(value: List<BarEntry>, average: Long = 0) {
        entries = value
        this.average = average
        // Bỏ chọn khi đổi kỳ: giữ lại chỉ số cũ sẽ trỏ sang một ngày khác hẳn.
        selectedIndex = NO_SELECTION
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (entries.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val slot = width.toFloat() / entries.size
                val index = (event.x / slot).toInt().coerceIn(entries.indices)
                // Chạm lại đúng cột đang chọn thì tắt bóng chú thích đi.
                selectedIndex = if (index == selectedIndex) NO_SELECTION else index
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty()) return

        val labelHeight = dp(16f)
        // Chừa sẵn chỗ cho bóng chú thích ngay cả khi chưa chạm: nếu chỉ chừa lúc
        // hiện bóng thì cả biểu đồ tụt xuống mỗi lần người dùng chạm vào.
        val topReserve = dp(22f)
        val baseline = height - labelHeight
        val chartHeight = baseline - topReserve

        // Chừa thêm 15% khoảng hở phía trên: cột cao nhất chạm sát mép trông như
        // bị cắt cụt. Cũng tránh chia cho 0 khi cả kỳ chưa có đơn nào.
        val maxValue = max(entries.maxOf { it.value }, 1L) * HEADROOM

        for (i in 1..3) {
            val y = topReserve + chartHeight * i / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        barPaint.shader = LinearGradient(
            0f, topReserve, 0f, baseline,
            barTopColor, barBottomColor, Shader.TileMode.CLAMP,
        )

        val slot = width.toFloat() / entries.size
        val barWidth = slot * BAR_WIDTH_RATIO
        val gap = (slot - barWidth) / 2f

        entries.forEachIndexed { index, entry ->
            val ratio = entry.value / maxValue
            val barHeight = chartHeight * ratio
            val left = index * slot + gap

            // Chỉ làm mờ khi người dùng ĐÃ chạm chọn một cột. Không mờ theo cột
            // "hôm nay": hôm nay thường chưa bán được gì, làm mờ mọi cột khác vì
            // một cột rỗng thì cả biểu đồ nhợt đi mà chẳng nhấn được gì.
            barPaint.alpha = when {
                selectedIndex == NO_SELECTION || selectedIndex == index -> FULL_ALPHA
                else -> MUTED_ALPHA
            }

            if (entry.value == 0L) {
                // Cột giá trị 0 vẫn vẽ một vạch mỏng để thấy rõ "ngày đó không
                // bán được gì", khác với "không có dữ liệu".
                barRect.set(left, baseline - dp(1.5f), left + barWidth, baseline)
                canvas.drawRect(barRect, barPaint)
            } else {
                barRect.set(left, baseline - barHeight, left + barWidth, baseline)
                barPath.reset()
                barPath.addRoundRect(barRect, barRadii, Path.Direction.CW)
                canvas.drawPath(barPath, barPaint)
            }

            // Nhiều cột thì chỉ ghi nhãn cách quãng để chữ không chồng lên nhau;
            // riêng nhãn của hôm nay luôn ghi, và ghi đậm màu thương hiệu để
            // người dùng định vị được mình đang ở đâu trong kỳ.
            val labelStep = if (entries.size > 10) entries.size / 5 else 1
            if (index % labelStep == 0 || entry.isHighlighted) {
                val paint = if (entry.isHighlighted) todayLabelPaint else labelPaint
                canvas.drawText(entry.label, left + barWidth / 2f, height - dp(3f), paint)
            }
        }
        barPaint.shader = null

        drawAverage(canvas, topReserve, chartHeight, baseline, maxValue)
        drawTooltip(canvas, slot, barWidth, gap, topReserve, chartHeight, baseline, maxValue)
    }

    private fun drawAverage(
        canvas: Canvas,
        topReserve: Float,
        chartHeight: Float,
        baseline: Float,
        maxValue: Float,
    ) {
        if (average <= 0L) return

        val y = baseline - chartHeight * (average / maxValue)
        // Trung bình cao hơn cả cột cao nhất là chuyện không thể xảy ra, nhưng
        // dữ liệu lệch vẫn có thể đẩy đường ra ngoài khung — kẹp lại cho chắc.
        if (y < topReserve || y > baseline) return

        canvas.drawLine(0f, y, width.toFloat(), y, averagePaint)
        canvas.drawText(AVERAGE_LABEL, width - dp(2f), y - dp(3f), averageLabelPaint)
    }

    private fun drawTooltip(
        canvas: Canvas,
        slot: Float,
        barWidth: Float,
        gap: Float,
        topReserve: Float,
        chartHeight: Float,
        baseline: Float,
        maxValue: Float,
    ) {
        val index = selectedIndex
        if (index == NO_SELECTION) return

        val entry = entries[index]
        val text = "${entry.label} · ${valueFormatter(entry.value)}"
        val textWidth = tooltipTextPaint.measureText(text)
        val paddingX = dp(8f)
        val paddingY = dp(4f)
        val boxWidth = textWidth + paddingX * 2
        val boxHeight = tooltipTextPaint.textSize + paddingY * 2

        val barCenter = index * slot + gap + barWidth / 2f
        // Kẹp vào trong khung: cột đầu và cột cuối sẽ đẩy bóng ra ngoài mép.
        val boxLeft = (barCenter - boxWidth / 2f).coerceIn(0f, width - boxWidth)

        val barTop = baseline - chartHeight * (entry.value / maxValue)
        val boxBottom = (barTop - dp(4f)).coerceAtLeast(boxHeight)
        tooltipRect.set(boxLeft, boxBottom - boxHeight, boxLeft + boxWidth, boxBottom)

        canvas.drawRoundRect(tooltipRect, dp(4f), dp(4f), tooltipPaint)

        val metrics = tooltipTextPaint.fontMetrics
        val textY = tooltipRect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, tooltipRect.centerX(), textY, tooltipTextPaint)
    }

    private companion object {
        const val NO_SELECTION = -1
        const val HEADROOM = 1.15f
        const val BAR_WIDTH_RATIO = 0.55f
        const val FULL_ALPHA = 255
        const val MUTED_ALPHA = 110
        const val GRID_ALPHA = 60
        const val AVERAGE_ALPHA = 130
        const val AVERAGE_LABEL = "TB"
    }
}
