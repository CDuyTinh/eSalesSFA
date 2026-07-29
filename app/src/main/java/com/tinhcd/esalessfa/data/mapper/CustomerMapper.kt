package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import com.tinhcd.esalessfa.domain.geo.GeoPoint
import com.tinhcd.esalessfa.domain.model.Customer

/**
 * Entity (Room) -> Domain model.
 *
 * Toạ độ gộp thành [GeoPoint] thay vì hai Double rời: chỉ có toạ độ khi CẢ hai
 * cùng khác null, nên kiểu dữ liệu tự loại bỏ trường hợp nửa vời.
 */
fun CustomerEntity.toDomain() = Customer(
    id = id,
    code = code,
    name = name,
    phone = phone,
    address = address,
    location = if (latitude != null && longitude != null) {
        GeoPoint(latitude, longitude)
    } else {
        null
    },
    channelId = channelId,
    priceGroupId = priceGroupId,
    creditLimit = creditLimit,
    debtAmount = debtAmount,
    imageUrl = imageUrl,
)
