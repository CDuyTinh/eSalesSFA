package com.tinhcd.esalessfa.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tinhcd.esalessfa.domain.model.product.Product
import com.tinhcd.esalessfa.domain.model.product.ProductUom
import com.tinhcd.esalessfa.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Quy tắc chọn đơn vị và tra giá của hộp nhập số lượng.
 *
 * Trước khi tách use case, những nhánh này nằm trong ViewModel nên chỉ kiểm được
 * bằng cách mở app rồi bấm tay.
 */
class LoadQtyOptionsUseCaseTest {

    private val thung = ProductUom(code = "THUNG", conversionRate = 24.0, isDefaultSale = true)
    private val le = ProductUom(code = "LE", conversionRate = 1.0, isDefaultSale = false)

    private val product = Product(
        id = "p1",
        code = "SP001",
        name = "Nước ngọt",
        baseUom = "LE",
        vatRate = 0.08,
        imageUrl = null,
        uoms = listOf(le, thung),
    )

    private val prices = mapOf("THUNG" to 240_000L, "LE" to 12_000L)

    @Test
    fun `khong truyen don vi thi lay don vi ban mac dinh`() = runTest {
        val useCase = LoadQtyOptionsUseCase(FakeProductRepository(product, prices))

        val options = useCase(productId = "p1", priceGroupId = "g1", preferredUom = null)

        assertThat(options.selectedUom).isEqualTo("THUNG")
        assertThat(options.unitPrice).isEqualTo(240_000L)
    }

    @Test
    fun `don vi dang sua duoc uu tien hon don vi mac dinh`() = runTest {
        val useCase = LoadQtyOptionsUseCase(FakeProductRepository(product, prices))

        val options = useCase(productId = "p1", priceGroupId = "g1", preferredUom = "LE")

        assertThat(options.selectedUom).isEqualTo("LE")
        assertThat(options.unitPrice).isEqualTo(12_000L)
    }

    /** Master data đồng bộ lại có thể bỏ một đơn vị mà dòng hàng cũ vẫn trỏ tới. */
    @Test
    fun `don vi khong con ton tai thi lui ve don vi mac dinh`() = runTest {
        val useCase = LoadQtyOptionsUseCase(FakeProductRepository(product, prices))

        val options = useCase(productId = "p1", priceGroupId = "g1", preferredUom = "KHAY")

        assertThat(options.selectedUom).isEqualTo("THUNG")
    }

    /** Thiếu giá không chặn ở đây — BuildOrderLineUseCase mới là chỗ từ chối. */
    @Test
    fun `thieu gia thi tra ve 0 chu khong no`() = runTest {
        val useCase = LoadQtyOptionsUseCase(FakeProductRepository(product, prices = emptyMap()))

        val options = useCase(productId = "p1", priceGroupId = "g1", preferredUom = null)

        assertThat(options.selectedUom).isEqualTo("THUNG")
        assertThat(options.unitPrice).isEqualTo(0L)
    }

    @Test
    fun `san pham khong con trong master thi tra ve rong`() = runTest {
        val useCase = LoadQtyOptionsUseCase(FakeProductRepository(product = null, prices))

        val options = useCase(productId = "p1", priceGroupId = "g1", preferredUom = null)

        assertThat(options.uoms).isEmpty()
        assertThat(options.selectedUom).isEmpty()
    }
}

private class FakeProductRepository(
    private val product: Product?,
    private val prices: Map<String, Long>,
) : ProductRepository {

    override fun search(query: String): Flow<List<Product>> = flowOf(emptyList())

    override suspend fun getById(id: String): Product? = product

    override suspend fun getPrice(
        priceGroupId: String,
        productId: String,
        uomCode: String,
    ): Long? = prices[uomCode]
}
