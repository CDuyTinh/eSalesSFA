package com.tinhcd.esalessfa.data.mapper

import com.tinhcd.esalessfa.core.database.entity.master.AppConfigEntity
import com.tinhcd.esalessfa.core.database.entity.master.BranchEntity
import com.tinhcd.esalessfa.core.database.entity.master.ChannelEntity
import com.tinhcd.esalessfa.core.database.entity.master.CustomerEntity
import com.tinhcd.esalessfa.core.database.entity.master.PriceGroupEntity
import com.tinhcd.esalessfa.core.database.entity.master.PriceListEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductCategoryEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductEntity
import com.tinhcd.esalessfa.core.database.entity.master.ProductUomEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionBreakEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionItemEntity
import com.tinhcd.esalessfa.core.database.entity.master.PromotionProgramEntity
import com.tinhcd.esalessfa.core.database.entity.master.ReasonCodeEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalesRouteDetailEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalesRouteEntity
import com.tinhcd.esalessfa.core.database.entity.master.SalespersonEntity
import com.tinhcd.esalessfa.core.database.entity.master.UomEntity
import com.tinhcd.esalessfa.core.network.dto.AppConfigDto
import com.tinhcd.esalessfa.core.network.dto.BranchDto
import com.tinhcd.esalessfa.core.network.dto.ChannelDto
import com.tinhcd.esalessfa.core.network.dto.CustomerDto
import com.tinhcd.esalessfa.core.network.dto.PriceGroupDto
import com.tinhcd.esalessfa.core.network.dto.PriceListDto
import com.tinhcd.esalessfa.core.network.dto.ProductCategoryDto
import com.tinhcd.esalessfa.core.network.dto.ProductDto
import com.tinhcd.esalessfa.core.network.dto.ProductUomDto
import com.tinhcd.esalessfa.core.network.dto.PromotionBreakDto
import com.tinhcd.esalessfa.core.network.dto.PromotionItemDto
import com.tinhcd.esalessfa.core.network.dto.PromotionProgramDto
import com.tinhcd.esalessfa.core.network.dto.ReasonCodeDto
import com.tinhcd.esalessfa.core.network.dto.SalesRouteDetailDto
import com.tinhcd.esalessfa.core.network.dto.SalesRouteDto
import com.tinhcd.esalessfa.core.network.dto.SalespersonDto
import com.tinhcd.esalessfa.core.network.dto.UomDto
import com.tinhcd.esalessfa.domain.util.SearchText

/**
 * DTO (server) -> Entity (local).
 *
 * rowVersion cố ý KHÔNG chuyển sang Entity: mốc version lưu tập trung một dòng
 * mỗi bảng ở sync_state, không nhân bản lên từng bản ghi.
 */

fun AppConfigDto.toEntity() = AppConfigEntity(code, value, dataType)

fun UomDto.toEntity() = UomEntity(code, name)

fun BranchDto.toEntity() = BranchEntity(id, code, name, address, latitude, longitude)

fun ChannelDto.toEntity() = ChannelEntity(id, code, name)

fun PriceGroupDto.toEntity() = PriceGroupEntity(id, code, name)

fun ReasonCodeDto.toEntity() = ReasonCodeEntity(id, code, name, applyFor, sortOrder)

fun SalespersonDto.toEntity() = SalespersonEntity(
    id = id,
    userId = userId,
    code = code,
    fullName = fullName,
    phone = phone,
    email = email,
    branchId = branchId,
    role = role,
    isActive = isActive,
)

fun CustomerDto.toEntity() = CustomerEntity(
    id = id,
    code = code,
    name = name,
    // Tự sinh chuỗi tìm kiếm, KHÔNG dùng cột name_search của server: chỉ cần
    // server sinh theo quy tắc khác là tìm kiếm trên máy chết mà client không
    // biết. Cùng một hàm với lúc chuẩn hoá từ khoá nên hai đầu luôn khớp.
    nameSearch = SearchText.normalize(name),
    phone = phone,
    address = address,
    latitude = latitude,
    longitude = longitude,
    channelId = channelId,
    priceGroupId = priceGroupId,
    branchId = branchId,
    salespersonId = salespersonId,
    creditLimit = creditLimit,
    debtAmount = debtAmount,
    imageUrl = imageUrl,
    isActive = isActive,
)

fun SalesRouteDto.toEntity() =
    SalesRouteEntity(id, code, name, salespersonId, dayOfWeek, weekPattern)

fun SalesRouteDetailDto.toEntity() =
    SalesRouteDetailEntity(id, routeId, customerId, sortOrder)

fun ProductCategoryDto.toEntity() =
    ProductCategoryEntity(id, code, name, parentId, sortOrder)

fun ProductDto.toEntity() = ProductEntity(
    id = id,
    code = code,
    name = name,
    nameSearch = SearchText.normalize(name),
    barcode = barcode,
    categoryId = categoryId,
    baseUom = baseUom,
    vatRate = vatRate,
    imageUrl = imageUrl,
    isTrackStock = isTrackStock,
    isActive = isActive,
)

fun ProductUomDto.toEntity() =
    ProductUomEntity(id, productId, uomCode, conversionRate, isDefaultSale, sortOrder)

fun PriceListDto.toEntity() =
    PriceListEntity(id, productId, priceGroupId, uomCode, price, fromDate, toDate)

fun PromotionProgramDto.toEntity() = PromotionProgramEntity(
    id = id,
    code = code,
    name = name,
    promoType = promoType,
    applyLevel = applyLevel,
    discountKind = discountKind,
    isAutoApply = isAutoApply,
    isMultiLevel = isMultiLevel,
    priority = priority,
    fromDate = fromDate,
    toDate = toDate,
    budgetAmount = budgetAmount,
    usedAmount = usedAmount,
    excludeProgramCodes = excludeProgramCodes,
)

fun PromotionBreakDto.toEntity() = PromotionBreakEntity(
    id = id,
    programId = programId,
    breakLevel = breakLevel,
    minQty = minQty,
    minAmount = minAmount,
    discountPct = discountPct,
    discountAmount = discountAmount,
    freeQty = freeQty,
    maxApplyTimes = maxApplyTimes,
)

fun PromotionItemDto.toEntity() = PromotionItemEntity(
    id = id,
    programId = programId,
    breakId = breakId,
    productId = productId,
    itemRole = itemRole,
    bundleGroup = bundleGroup,
    requiredQty = requiredQty,
    uomCode = uomCode,
    freeStockQty = freeStockQty,
)
