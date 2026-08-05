package com.tinhcd.esalessfa.domain.model.util

import java.text.Normalizer

/**
 * Chuẩn hoá chuỗi để tìm kiếm không dấu.
 *
 * Đây là MỘT hàm duy nhất dùng cho cả hai đầu: lúc ghi cột tìm kiếm vào Room, và
 * lúc chuẩn hoá từ khoá người dùng gõ. Nếu hai đầu dùng hai cách chuẩn hoá khác
 * nhau thì tìm kiếm sẽ im lặng không ra kết quả — không có lỗi, không có log,
 * chỉ là danh sách rỗng.
 *
 * Cố ý KHÔNG tin cột name_search do server gửi: nó có thể được sinh bằng quy tắc
 * khác, và một lần server sinh sai là toàn bộ tìm kiếm trên máy chết theo mà
 * client không có cách nào biết.
 */
object SearchText {

    /** Ký tự Việt không nằm trong dạng tổ hợp NFD nên phải thay tay. */
    private val extraMappings = mapOf(
        'đ' to 'd', 'Đ' to 'D',
    )

    private val combiningMarks = Regex("\\p{Mn}+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""

        // NFD tách chữ và dấu thành hai ký tự riêng, sau đó xoá phần dấu.
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        val stripped = combiningMarks.replace(decomposed, "")

        return buildString(stripped.length) {
            for (char in stripped) {
                append(extraMappings[char] ?: char)
            }
        }
            .lowercase()
            // Gộp khoảng trắng liên tiếp: gõ "minh  anh" vẫn khớp "minh anh".
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
