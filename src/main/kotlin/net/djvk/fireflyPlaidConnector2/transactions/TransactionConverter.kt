package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.plaid.models.Transaction as PlaidTransaction
import net.djvk.fireflyPlaidConnector2.api.firefly.models.Transaction as FireflyTransaction
import net.djvk.fireflyPlaidConnector2.categories.PlaidOldCategoryCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TransactionConverter(
    private val categoryCache: PlaidOldCategoryCache,

    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun convert(plaidT: PlaidTransaction): FireflyTransaction {

    }
}