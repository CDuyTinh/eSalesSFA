package com.tinhcd.esalessfa.domain.model.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchTextTest {

    @Test
    fun `bo dau tieng viet`() {
        assertThat(SearchText.normalize("Cửa hàng Minh Anh"))
            .isEqualTo("cua hang minh anh")
    }

    @Test
    fun `xu ly du chin nguyen am co dau`() {
        assertThat(SearchText.normalize("ăâêôơưáàảãạ")).isEqualTo("aaeoouaaaaa")
    }

    @Test
    fun `chu d gach ngang thanh d thuong`() {
        // đ không nằm trong dạng tổ hợp NFD nên phải thay tay; thiếu bước này thì
        // gõ "dai loi" không ra "Đại Lợi".
        assertThat(SearchText.normalize("Đại Lợi")).isEqualTo("dai loi")
    }

    @Test
    fun `gop khoang trang lien tiep`() {
        assertThat(SearchText.normalize("Minh   Anh")).isEqualTo("minh anh")
    }

    @Test
    fun `cat khoang trang dau cuoi`() {
        assertThat(SearchText.normalize("  Minh Anh  ")).isEqualTo("minh anh")
    }

    @Test
    fun `chuoi rong tra ve rong`() {
        assertThat(SearchText.normalize("")).isEmpty()
        assertThat(SearchText.normalize("   ")).isEmpty()
    }

    @Test
    fun `giu nguyen chu so va ky tu khong dau`() {
        assertThat(SearchText.normalize("KH0012")).isEqualTo("kh0012")
    }

    /**
     * Tính chất quan trọng nhất: chuẩn hoá tên rồi tìm bằng từ khoá đã chuẩn hoá
     * phải khớp. Đây chính là điều kiện để tìm kiếm hoạt động.
     */
    @Test
    fun `tu khoa co dau khop voi ten da chuan hoa`() {
        val stored = SearchText.normalize("Cửa hàng Thành Đạt 42")

        assertThat(stored).contains(SearchText.normalize("Thành Đạt"))
        assertThat(stored).contains(SearchText.normalize("thanh dat"))
        assertThat(stored).contains(SearchText.normalize("THÀNH ĐẠT"))
    }

    @Test
    fun `chuan hoa hai lan cho ket qua giong nhau`() {
        val once = SearchText.normalize("Cửa hàng Hồng Phúc")

        assertThat(SearchText.normalize(once)).isEqualTo(once)
    }
}
