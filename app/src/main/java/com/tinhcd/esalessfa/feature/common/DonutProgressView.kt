package com.tinhcd.esalessfa.feature.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.tinhcd.esalessfa.R

/**
 * Vòng tròn tiến độ có phần trăm ở giữa, theo đúng cách bản eSales gốc hiển thị
 * KPI tháng.
 *
 * Tự vẽ thay vì dùng CircularProgressIndicator của Material: cái đó vẽ vòng bo
 * đầu và có animation riêng, còn ở đây cần một vành mảnh, màu nhạt cho phần
 * chưa đạt, và chữ nằm chính giữa — ghép lại bằng FrameLayout sẽ tốn thêm hai
 * lớp view cho mỗi dòng KPI.
 */
class DonutProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val arcRect = RectF()

    /** 0f..1f. */
    private var ratio: Float = 0f

    init {
        val strokeWidth = resources.getDimension(R.dimen.donut_stroke)
        trackPaint.strokeWidth = strokeWidth
        arcPaint.strokeWidth = strokeWidth
        textPaint.textSize = resources.getDimension(R.dimen.donut_text)
        setColor(context.getColor(R.color.brandRed))
    }

    /**
     * Đặt màu cho cả vành và chữ; phần chưa đạt dùng chính màu đó nhưng mờ đi.
     *
     * Một màu cho mỗi KPI giúp mắt nhận ra dòng nào là dòng nào khi cuộn nhanh,
     * đúng như bản gốc dùng xanh/đỏ/cam cho từng chỉ số.
     */
    fun setColor(@ColorInt color: Int) {
        arcPaint.color = color
        textPaint.color = color
        trackPaint.color = color
        trackPaint.alpha = TRACK_ALPHA
        invalidate()
    }

    fun setRatio(value: Float) {
        ratio = value.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = arcPaint.strokeWidth / 2f
        arcRect.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
        // Bắt đầu từ -90 độ: phần đã đạt chạy từ đỉnh vòng tròn theo chiều kim
        // đồng hồ, giống mọi biểu đồ tiến độ người dùng đã quen.
        canvas.drawArc(arcRect, START_ANGLE, 360f * ratio, false, arcPaint)

        // Canh chữ theo baseline chứ không theo tâm: descent/ascent lệch nhau nên
        // đặt y = tâm sẽ nhìn như chữ bị tụt xuống.
        val metrics = textPaint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("${(ratio * 100).toInt()}%", width / 2f, baseline, textPaint)
    }

    private companion object {
        const val START_ANGLE = -90f
        const val TRACK_ALPHA = 48
    }
}
