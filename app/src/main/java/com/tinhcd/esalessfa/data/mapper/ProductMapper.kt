package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.ProductEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductUomEntity
import com.tinhcd.esalessfa.domain.model.product.Product
import com.tinhcd.esalessfa.domain.model.product.ProductUom

fun ProductEntity.toDomain(uoms: List<ProductUomEntity>) = Product(
    id = id,
    code = code,
    name = name,
    baseUom = baseUom,
    vatRate = vatRate,
    imageUrl = imageUrl,
    uoms = uoms.map { ProductUom(it.uomCode, it.conversionRate, it.isDefaultSale) },
)
