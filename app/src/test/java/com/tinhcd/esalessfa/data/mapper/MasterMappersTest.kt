package com.tinhcd.esalessfa.data.mapper

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.core.network.dto.CustomerDto
import com.tinhcd.esalessfa.core.network.dto.PriceListDto
import com.tinhcd.esalessfa.core.network.dto.ProductUomDto
import com.tinhcd.esalessfa.core.network.dto.PromotionBreakDto
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Kiểm tra ánh xạ JSON của server -> Entity.
 *
 * Gõ nhầm một @SerialName không làm build hỏng và cũng không ném lỗi lúc chạy —
 * trường đó chỉ lặng lẽ nhận giá trị mặc định. Với `price` hay `conversion_rate`
 * thì đó là đơn hàng sai tiền mà không ai biết. Test này chặn đúng chỗ đó.
 */
class MasterMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decode customer keeps snake_case fields from server`() {
        val payload = """
            {
              "id": "c1", "code": "KH0001", "name": "Cửa hàng Minh Anh",
              "name_search": "cua hang minh anh",
              "price_group_id": "pg1", "branch_id": "b1", "salesperson_id": "s1",
              "credit_limit": 50000000, "debt_amount": 1200000,
              "latitude": 10.7769, "longitude": 106.7009,
              "is_active": true, "row_version": 5035
            }
        """.trimIndent()

        val entity = json.decodeFromString<CustomerDto>(payload).toEntity()

        assertThat(entity.id).isEqualTo("c1")
        assertThat(entity.nameSearch).isEqualTo("cua hang minh anh")
        assertThat(entity.priceGroupId).isEqualTo("pg1")
        assertThat(entity.branchId).isEqualTo("b1")
        assertThat(entity.salespersonId).isEqualTo("s1")
        assertThat(entity.creditLimit).isEqualTo(50_000_000L)
        assertThat(entity.debtAmount).isEqualTo(1_200_000L)
        assertThat(entity.latitude).isWithin(1e-9).of(10.7769)
    }

    /**
     * Chuỗi tìm kiếm do CLIENT sinh từ tên, bỏ dấu — không lấy cột name_search
     * của server.
     *
     * Trước đây lấy nguyên từ server, và server sinh thiếu phần tên riêng
     * ("cua hang 12" thay vì "cua hang minh anh 12") nên gõ tên khách hàng không
     * ra kết quả nào.
     */
    @Test
    fun `search text is generated from name with diacritics stripped`() {
        val payload = """
            {"id":"c2","code":"KH0002","name":"Tạp Hoá ABC",
             "price_group_id":"pg1","branch_id":"b1","row_version":1}
        """.trimIndent()

        val entity = json.decodeFromString<CustomerDto>(payload).toEntity()

        assertThat(entity.nameSearch).isEqualTo("tap hoa abc")
    }

    @Test
    fun `search text ignores the value sent by server`() {
        // Server gửi chuỗi thiếu tên riêng — đúng lỗi từng gặp trong seed data.
        val payload = """
            {"id":"c9","code":"KH0009","name":"Cửa hàng Minh Anh 9",
             "name_search":"cua hang 9",
             "price_group_id":"pg1","branch_id":"b1","row_version":1}
        """.trimIndent()

        val entity = json.decodeFromString<CustomerDto>(payload).toEntity()

        assertThat(entity.nameSearch).isEqualTo("cua hang minh anh 9")
    }

    @Test
    fun `price is decoded as Long to avoid floating point drift`() {
        val payload = """
            {"id":"p1","product_id":"pr1","price_group_id":"pg1","uom_code":"THUNG",
             "price":249840,"from_date":"2025-01-01","to_date":"2099-12-31","row_version":10}
        """.trimIndent()

        val entity = json.decodeFromString<PriceListDto>(payload).toEntity()

        assertThat(entity.price).isEqualTo(249_840L)
        assertThat(entity.uomCode).isEqualTo("THUNG")
        assertThat(entity.fromDate).isEqualTo("2025-01-01")
    }

    @Test
    fun `conversion rate survives mapping`() {
        val payload = """
            {"id":"u1","product_id":"pr1","uom_code":"THUNG","conversion_rate":24.0,
             "is_default_sale":true,"sort_order":3,"row_version":5}
        """.trimIndent()

        val entity = json.decodeFromString<ProductUomDto>(payload).toEntity()

        assertThat(entity.conversionRate).isWithin(1e-9).of(24.0)
        assertThat(entity.isDefaultSale).isTrue()
    }

    @Test
    fun `promotion break keeps null condition distinct from zero`() {
        // Bậc xét theo số lượng thì min_amount phải là null, KHÔNG phải 0.
        // Nếu thành 0, engine sẽ hiểu là "đơn từ 0 đồng trở lên" và áp khuyến mãi
        // cho mọi đơn hàng.
        val payload = """
            {"id":"b1","program_id":"km1","break_level":1,"min_qty":10.0,
             "discount_pct":0.03,"row_version":20}
        """.trimIndent()

        val entity = json.decodeFromString<PromotionBreakDto>(payload).toEntity()

        assertThat(entity.minQty).isNotNull()
        assertThat(entity.minQty!!).isWithin(1e-9).of(10.0)
        assertThat(entity.minAmount).isNull()
        assertThat(entity.discountAmount).isEqualTo(0L)
    }

    @Test
    fun `unknown server columns do not break decoding`() {
        // Server thêm cột mới -> app phiên bản cũ vẫn phải sync được.
        val payload = """
            {"id":"c3","code":"KH0003","name":"Test","price_group_id":"pg1",
             "branch_id":"b1","row_version":1,"cot_moi_server_vua_them":"abc"}
        """.trimIndent()

        val entity = json.decodeFromString<CustomerDto>(payload).toEntity()

        assertThat(entity.code).isEqualTo("KH0003")
    }
}
