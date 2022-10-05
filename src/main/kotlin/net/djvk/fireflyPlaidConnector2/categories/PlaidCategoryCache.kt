package net.djvk.fireflyPlaidConnector2.categories

import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

typealias PlaidOldCategoryId = Int
typealias PlaidOldCategory = String
typealias PlaidOldCategoryMap = Map<PlaidOldCategoryId, List<PlaidOldCategory>>

@Component
class PlaidOldCategoryCache(
    private val plaidApi: PlaidApi,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private var cache: PlaidOldCategoryMap? = null

    private suspend fun fetchCategories(): PlaidOldCategoryMap {
        val response = plaidApi.categoriesGet({}).body()
        logger.info("categories: $response")

        return mapOf()
    }

    suspend fun getOldCategory(categoryId: PlaidOldCategoryId): List<PlaidOldCategory> {
        val categoryMap = cache ?: run {
            val map = fetchCategories()
            cache = map
            map
        }

        return categoryMap[categoryId]
            ?: throw RuntimeException("Failed to find mapping for old Plaid category id $categoryId")
    }
}