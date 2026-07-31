package com.tinhcd.esalessfa.feature.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Hoạ tiết dải sóng xoắn ở màn Splash và Login.
 *
 * Bản gốc dùng ảnh PNG (`img_flashscreen`, `img_login_bottom`) nặng gần 3MB cho
 * cả ba mật độ. Vẽ lại bằng Canvas thì tốn 0 byte tài nguyên, trải khít mọi tỉ
 * lệ màn hình mà không bị kéo méo, và không mang theo tài sản thiết kế của
 * công ty khác vào một project cá nhân.
 *
 * Cách tạo hình — mô phỏng một dải băng bị xoắn:
 *
 *     x(y) = trục(y) + bề_rộng(y) * sin(góc_đường + độ_xoắn * y)
 *
 * - `trục(y)` uốn lượn để cả dải băng bò dọc màn hình.
 * - `bề_rộng(y)` là hình sin nên có những điểm bằng 0: đó là chỗ dải băng quay
 *   cạnh về phía người xem, mọi đường tụ lại thành một nút thắt. Ba bụng sóng
 *   giữa các nút thắt chính là ba vùng phình của hoạ tiết gốc.
 * - `góc_đường` rải các đường quanh chu vi dải băng, còn `độ_xoắn` làm chúng
 *   trượt lên nhau dọc chiều cao — đó là lý do các đường cắt nhau thành mắt lưới
 *   thay vì chỉ chạy song song.
 */
class RibbonPatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = LINE_COLOR
    }

    /**
     * Dựng sẵn đường đi ngay khi biết kích thước.
     *
     * Hoạ tiết tĩnh nên tính lại 40 × 64 toạ độ trong mỗi onDraw là công vô ích —
     * màn Login còn vẽ lại mỗi lần bàn phím bật/tắt.
     */
    private val paths = List(LINE_COUNT) { Path() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        val width = w.toFloat()
        val height = h.toFloat()

        paths.forEachIndexed { index, path ->
            path.rewind()
            val angle = index * (2f * PI.toFloat() / LINE_COUNT)

            for (step in 0..SEGMENTS) {
                val t = step / SEGMENTS.toFloat()

                val spine = width * (0.46f + 0.30f * sin(t * SPINE_TURNS + 1.6f))
                val halfWidth = width * 0.21f * sin(t * PINCH_TURNS + 0.5f)
                val x = spine + halfWidth * sin(angle + t * TWIST_TURNS)

                if (step == 0) path.moveTo(x, t * height) else path.lineTo(x, t * height)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        paths.forEach { canvas.drawPath(it, paint) }
    }

    private companion object {
        /** Đỏ sáng hơn nền và mờ đi, để hoạ tiết chìm hẳn xuống dưới nội dung. */
        val LINE_COLOR = Color.argb(0x44, 0xFF, 0x4A, 0x40)

        const val LINE_COUNT = 34
        const val SEGMENTS = 72

        /** ~1,2 vòng: dải băng uốn một nhịp chữ S dọc màn hình. */
        val SPINE_TURNS = 2.4f * PI.toFloat()

        /** 1,7 vòng: cho hai nút thắt nằm trong màn hình, chia ra ba vùng phình.
            Cố ý không lấy số nguyên vòng để ba vùng phình không đều nhau — đều
            nhau thì hoạ tiết trông như hình in máy móc. */
        val PINCH_TURNS = 3.4f * PI.toFloat()

        /** ~2,9 vòng xoắn: quyết định mắt lưới thưa hay dày. */
        val TWIST_TURNS = 5.8f * PI.toFloat()
    }
}
