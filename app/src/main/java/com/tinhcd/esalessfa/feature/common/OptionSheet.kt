package com.tinhcd.esalessfa.feature.common

import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.tinhcd.esalessfa.databinding.ItemOptionRowBinding
import com.tinhcd.esalessfa.databinding.SheetOptionListBinding

/**
 * Hộp chọn một trong nhiều, hiện lên từ đáy màn hình.
 *
 * Dùng thay cho dialog danh sách của AlertDialog: hộp thoại giữa màn che mất
 * bối cảnh và nút bấm nằm xa ngón cái, trong khi nhân viên cầm máy một tay giữa
 * cửa hàng. Bottom sheet trồi lên ngay tầm ngón, vuốt xuống là đóng.
 *
 * Dialog thường chứ không phải DialogFragment: hộp này không giữ trạng thái gì,
 * xoay máy thì đóng luôn cũng không mất mát gì — đúng như dialog nó thay thế.
 */
fun Fragment.showOptionSheet(
    @StringRes title: Int,
    options: List<String>,
    onPick: (Int) -> Unit,
) {
    val inflater = LayoutInflater.from(requireContext())
    val binding = SheetOptionListBinding.inflate(inflater)
    val dialog = BottomSheetDialog(requireContext())

    binding.sheetTitle.setText(title)

    options.forEachIndexed { index, label ->
        val row = ItemOptionRowBinding.inflate(inflater, binding.optionContainer, false)
        row.optionLabel.text = label
        row.root.setOnClickListener {
            // Đóng TRƯỚC khi gọi tiếp: nhánh xử lý thường là điều hướng sang màn
            // khác, đóng sau thì hộp còn nằm lại chớp một nhịp trên màn mới.
            dialog.dismiss()
            onPick(index)
        }
        binding.optionContainer.addView(row.root)
    }

    dialog.setContentView(binding.root)
    dialog.show()
}
