package com.tinhcd.esalessfa.domain.model

data class Salesperson(
    val id: String,
    val code: String,
    val fullName: String,
    val branchId: String?,
    val role: String,
)
