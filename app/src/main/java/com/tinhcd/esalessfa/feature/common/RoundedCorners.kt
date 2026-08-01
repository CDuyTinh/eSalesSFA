package com.tinhcd.esalessfa.feature.common

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

/**
 * Bo hai góc DƯỚI của view và cắt luôn mọi thứ vẽ bên trong nó.
 *
 * Dùng cho khối ảnh đầu màn chi tiết: nền, ảnh và dải tối phải cùng bo theo một
 * đường. Đặt nền bo góc (shape drawable) hay MaterialCardView với bốn bán kính
 * khác nhau đều không đủ — chúng chỉ bo phần NỀN, còn view con vẫn vẽ tràn ra
 * hai góc vuông, để lại hai mảng tối ở đáy.
 *
 * Cắt bằng outline thì cả cây con bị cắt. Outline chỉ nhận được hình chữ nhật
 * bo đều bốn góc, nên kéo hình lên quá mép trên đúng bằng bán kính: hai góc
 * trên rơi ra ngoài view, chỉ còn hai góc dưới hiện ra.
 */
fun View.roundBottomCorners(radius: Float) {
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, -radius.toInt(), view.width, view.height, radius)
        }
    }
    clipToOutline = true
}
