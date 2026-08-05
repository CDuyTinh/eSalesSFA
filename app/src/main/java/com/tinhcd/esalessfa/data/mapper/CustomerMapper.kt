package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import com.tinhcd.esalessfa.domain.model.customer.Customer
import com.tinhcd.esalessfa.domain.model.geo.GeoPoint

/**
 * Entity (Room) -> Domain model.
 *
 * Toạ độ gộp thành [GeoPoint] thay vì hai Double rời: chỉ có toạ độ khi CẢ hai
 * cùng khác null, nên kiểu dữ liệu tự loại bỏ trường hợp nửa vời.
 */
fun CustomerEntity.toDomain(channelName: String? = null) = Customer(
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
    channelName = channelName,
    priceGroupId = priceGroupId,
    creditLimit = creditLimit,
    debtAmount = debtAmount,
    imageUrl = imageUrl,
)
