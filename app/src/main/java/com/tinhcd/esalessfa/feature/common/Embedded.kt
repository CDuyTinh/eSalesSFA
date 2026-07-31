package com.tinhcd.esalessfa.feature.common

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

/**
 * Cờ cho biết fragment đang được nhúng làm một tab trong màn Home, chứ không mở
 * toàn màn hình như một đích điều hướng riêng.
 *
 * Fragment nhúng phải bỏ mũi tên back trên toolbar của nó: nó gọi navigateUp()
 * và sẽ pop cả màn Home ra khỏi back stack, đẩy người dùng ngược về màn Sync
 * hoặc Login. Tab thì không có gì để "quay lại" — thanh tab chính là đường đi.
 */
const val ARG_EMBEDDED = "embedded"

val Fragment.isEmbedded: Boolean
    get() = arguments?.getBoolean(ARG_EMBEDDED) == true

/** Gắn cờ [ARG_EMBEDDED] cùng các tham số khác cho fragment sắp dùng làm tab. */
fun <F : Fragment> F.asTab(vararg args: Pair<String, Any?>): F = apply {
    arguments = bundleOf(ARG_EMBEDDED to true, *args)
}
