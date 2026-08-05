package com.tinhcd.esalessfa.domain.repository

import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeProductCount(): Flow<Int>
    fun observeActivePromotionCount(): Flow<Int>
}
