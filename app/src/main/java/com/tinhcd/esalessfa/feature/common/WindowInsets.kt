package com.tinhcd.esalessfa.feature.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding

/**
 * Chừa chỗ cho status bar bằng padding TRÊN của chính view này.
 *
 * Nền không bị padding cắt, nên dải đỏ của màn hình vẫn chạy hết lên đỉnh máy
 * đúng như bản eSales gốc, còn toolbar và nội dung thì tụt xuống dưới đồng hồ.
 * Đây là lý do phải dùng padding chứ không phải margin hay một View đệm.
 *
 * Gọi trong onViewCreated, trên view GỐC của mỗi màn toàn khung. Padding khai
 * trong layout được cộng thêm chứ không bị ghi đè; mỗi lần dựng lại là một View
 * mới nên padding cũng không dồn lên nhau.
 */
fun View.padTopForStatusBar() {
    val declaredTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val statusBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        v.updatePadding(top = declaredTop + statusBar)
        insets
    }
    // View của fragment gắn vào một cây đã layout xong, hệ thống sẽ không phát
    // lại insets nếu chúng không đổi — phải tự xin một lượt dispatch. doOnAttach
    // vì lúc onViewCreated view có thể chưa vào cây, xin lúc đó là vô ích.
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
