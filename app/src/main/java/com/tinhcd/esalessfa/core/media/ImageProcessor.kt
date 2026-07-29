package com.tinhcd.esalessfa.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.tinhcd.esalessfa.core.common.dispatcher.DispatcherProvider
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ProcessedPhoto(val file: File, val sizeBytes: Int)

/**
 * Nén và đóng dấu ảnh minh chứng.
 *
 * Ảnh gốc từ camera điện thoại thường 3–8 MB. Nhân viên chụp 10 ảnh mỗi cửa
 * hàng, 30 cửa hàng một ngày — không nén thì vừa hết dung lượng máy vừa không
 * upload nổi qua 3G ngoài thị trường.
 */
@Singleton
class ImageProcessor @Inject constructor(
    private val dispatchers: DispatcherProvider,
) {

    suspend fun process(
        source: File,
        target: File,
        location: GeoPoint?,
        customerName: String,
    ): ProcessedPhoto = withContext(dispatchers.io) {
        val decoded = decodeScaled(source)
        val rotated = applyExifRotation(decoded, source)
        val stamped = drawWatermark(rotated, location, customerName)

        // Giảm dần chất lượng cho tới khi đạt ngưỡng. Đặt cứng quality = 70 sẽ
        // cho file 200KB với ảnh đơn giản nhưng 1,5MB với kệ hàng nhiều chi tiết.
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = java.io.ByteArrayOutputStream().use { out ->
                stamped.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (bytes.size > MAX_BYTES && quality >= MIN_QUALITY)

        target.parentFile?.mkdirs()
        target.writeBytes(bytes)

        if (stamped != rotated) stamped.recycle()
        if (rotated != decoded) rotated.recycle()
        decoded.recycle()

        ProcessedPhoto(target, bytes.size)
    }

    /**
     * Giải mã ở kích thước đã thu nhỏ.
     *
     * Đọc nguyên ảnh 12MP vào RAM rồi mới scale sẽ ngốn ~48MB và dễ OutOfMemory
     * trên máy tầm thấp. inSampleSize giảm ngay lúc đọc.
     */
    private fun decodeScaled(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Không đọc được ảnh ${file.name}")
    }

    /**
     * Xoay ảnh theo thẻ EXIF.
     *
     * Nhiều máy lưu ảnh nằm ngang kèm cờ xoay thay vì xoay pixel thật. Bỏ qua
     * bước này thì ảnh chụp dọc sẽ nằm ngang sau khi nén, vì thao tác nén làm
     * mất thẻ EXIF.
     */
    private fun applyExifRotation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Đóng dấu thời gian, toạ độ và tên cửa hàng lên ảnh.
     *
     * Đây là biện pháp chống gian lận: ảnh chụp sẵn từ hôm trước hoặc lấy từ
     * cửa hàng khác sẽ lộ ngay vì dấu không khớp với lượt viếng thăm.
     */
    private fun drawWatermark(
        bitmap: Bitmap,
        location: GeoPoint?,
        customerName: String,
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        val canvas = Canvas(result)

        val textSize = result.width * 0.035f
        val padding = textSize * 0.6f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
        }
        val bgPaint = Paint().apply {
            color = Color.BLACK
            alpha = 140
        }

        val lines = buildList {
            add(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()))
            add(customerName)
            location?.let { add("%.6f, %.6f".format(it.latitude, it.longitude)) }
        }

        val blockHeight = lines.size * (textSize + padding / 2) + padding
        canvas.drawRect(
            0f,
            result.height - blockHeight,
            result.width.toFloat(),
            result.height.toFloat(),
            bgPaint,
        )

        var y = result.height - blockHeight + textSize + padding / 2
        lines.forEach { line ->
            canvas.drawText(line, padding, y, textPaint)
            y += textSize + padding / 2
        }

        return result
    }

    private companion object {
        /** Cạnh dài tối đa; đủ để đọc nhãn sản phẩm trên kệ khi phóng to. */
        const val MAX_DIMENSION = 1600

        /** Ngưỡng dung lượng mỗi ảnh sau nén. */
        const val MAX_BYTES = 300 * 1024

        const val START_QUALITY = 85
        const val MIN_QUALITY = 40
        const val QUALITY_STEP = 10
    }
}
