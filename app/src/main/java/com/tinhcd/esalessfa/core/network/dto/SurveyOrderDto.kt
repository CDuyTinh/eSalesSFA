package com.tinhcd.esalessfa.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Cấu hình khảo sát ────────────────────────────────────────────────────────

@Serializable
data class SurveyTypeDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("pass_score") val passScore: Double = 0.0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SurveyQuestionGroupDto(
    val id: String,
    @SerialName("survey_type_id") val surveyTypeId: String,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SurveyQuestionDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val code: String,
    val content: String,
    @SerialName("answer_type") val answerType: String,
    @SerialName("is_required") val isRequired: Boolean = true,
    val score: Double = 0.0,
    @SerialName("min_photo") val minPhoto: Int = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class SurveyQuestionOptionDto(
    val id: String,
    @SerialName("question_id") val questionId: String,
    val code: String,
    val content: String,
    val score: Double = 0.0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("row_version") val rowVersion: Long,
)

// ── Lịch sử đơn hàng tải ngược về ────────────────────────────────────────────

@Serializable
data class OrderDownloadDto(
    val id: String,
    @SerialName("order_no") val orderNo: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("salesperson_id") val salespersonId: String,
    @SerialName("visit_id") val visitId: String? = null,
    @SerialName("branch_id") val branchId: String,
    @SerialName("order_date") val orderDate: String,
    @SerialName("delivery_date") val deliveryDate: String? = null,
    val status: String,
    @SerialName("sub_total") val subTotal: Long = 0,
    @SerialName("discount_amount") val discountAmount: Long = 0,
    @SerialName("manual_discount") val manualDiscount: Long = 0,
    @SerialName("net_amount") val netAmount: Long = 0,
    @SerialName("vat_amount") val vatAmount: Long = 0,
    @SerialName("total_amount") val totalAmount: Long = 0,
    val note: String? = null,
    @SerialName("reason_code") val reasonCode: String? = null,
    @SerialName("row_version") val rowVersion: Long,
)

@Serializable
data class OrderDetailDownloadDto(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("line_no") val lineNo: Int,
    @SerialName("product_id") val productId: String,
    @SerialName("uom_code") val uomCode: String,
    val qty: Double,
    @SerialName("conversion_rate") val conversionRate: Double,
    @SerialName("base_qty") val baseQty: Double,
    val price: Long,
    @SerialName("gross_amount") val grossAmount: Long,
    @SerialName("discount_amount") val discountAmount: Long,
    @SerialName("net_amount") val netAmount: Long,
    @SerialName("vat_rate") val vatRate: Double,
    @SerialName("vat_amount") val vatAmount: Long,
    @SerialName("line_amount") val lineAmount: Long,
    @SerialName("is_free_item") val isFreeItem: Boolean = false,
    @SerialName("promotion_id") val promotionId: String? = null,
    @SerialName("row_version") val rowVersion: Long,
)
