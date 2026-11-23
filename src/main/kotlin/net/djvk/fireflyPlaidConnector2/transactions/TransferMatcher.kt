package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionTypeProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.ZoneId
import kotlin.math.abs

/**
 * Identifies transaction pairs that represent a transfer from one account into another.
 */
@Component
class TransferMatcher(
    @Value("\${fireflyPlaidConnector2.timeZone}")
    private val timeZoneString: String,

    @Value("\${fireflyPlaidConnector2.transferMatchWindowDays}")
    private val transferMatchWindowDays: Long,
) {
    private val zoneId = ZoneId.of(timeZoneString)
    private val transferMatchWindowSeconds = transferMatchWindowDays * 24 * 60 * 60
    private val logger = LoggerFactory.getLogger(this::class.java)

    val transferTypes =
        setOf(
            PersonalFinanceCategoryEnum.Primary.TRANSFER_IN,
            PersonalFinanceCategoryEnum.Primary.TRANSFER_OUT,
            PersonalFinanceCategoryEnum.Primary.LOAN_PAYMENTS,
            PersonalFinanceCategoryEnum.Primary.BANK_FEES,
        )

    /**
     * Match transactions that are each other's uniquely closest eligible partner.
     */
    fun match(txs: List<PlaidFireflyTransaction>): List<PlaidFireflyTransaction> {
        logger.trace("Starting ${::match.name}")

        if (transferMatchWindowSeconds <= 0) return txs

        // Split-out the transactions that are unlikely to be transfers based on their category. If we're not sure,
        // we'll try to match it as a transfer anyway.
        val (possibleTransfers, nonTransfers) = txs.partition {
            val category = it.plaidTransaction?.personalFinanceCategory
            category == null || transferTypes.contains(PersonalFinanceCategoryEnum.from(category).primary)
        }

        val results = nonTransfers.toMutableList()

        val candidateCounts = txs.groupingBy(::candidateId).eachCount()
        val groups = mutableMapOf<MatchKey, MutableList<Candidate>>()
        for (tx in possibleTransfers) {
            val id = candidateId(tx)
            val currency = matchCurrency(tx)
            val amount = matchAmount(tx)
            if (id == null || candidateCounts.getValue(id) > 1 ||
                currency == null || amount == null || amount.signum() == 0
            ) {
                results.add(tx)
                continue
            }

            val key = MatchKey(currency, amount.abs().stripTrailingZeros())
            groups.getOrPut(key) { mutableListOf() }.add(
                Candidate(tx, amount.signum() > 0, tx.getTimestamp(zoneId).toEpochSecond())
            )
        }

        for (group in groups.values) {
            findClosestPartners(group)
            for (candidate in group) {
                val tiedDistance = candidate.closestTieDistance
                if (tiedDistance != null) {
                    logger.warn(
                        "Leaving transaction {} separate because its closest eligible transfer candidates are tied at {} seconds",
                        candidateId(candidate.tx),
                        tiedDistance,
                    )
                }

                val partner = candidate.closest
                if (partner == null || partner.closest !== candidate) {
                    results.add(candidate.tx)
                } else if (candidate.isWithdrawal) {
                    results.add(PlaidFireflyTransaction.Transfer.create(candidate.tx, partner.tx))
                }
            }
        }

        return results
    }

    private fun candidateId(tx: PlaidFireflyTransaction): String? {
        val fireflyId = tx.fireflyTransaction?.id
        if (fireflyId != null) return "firefly:$fireflyId"
        return tx.plaidTransaction?.transactionId?.let { "plaid:$it" }
    }

    private fun findClosestPartners(group: List<Candidate>) {
        val (withdrawals, deposits) = group.sortedBy { it.timestamp }.partition { it.isWithdrawal }
        var firstDepositInWindow = 0
        for (withdrawal in withdrawals) {
            while (firstDepositInWindow < deposits.size &&
                withdrawal.timestamp - deposits[firstDepositInWindow].timestamp >= transferMatchWindowSeconds
            ) {
                firstDepositInWindow++
            }
            for (index in firstDepositInWindow until deposits.size) {
                val deposit = deposits[index]
                val secondsDiff = deposit.timestamp - withdrawal.timestamp
                if (secondsDiff >= transferMatchWindowSeconds) break

                // A replacement can use at most one existing Firefly entry.
                if (withdrawal.tx.fireflyTransaction != null && deposit.tx.fireflyTransaction != null) continue
                if (withdrawal.tx.fireflyAccountId == deposit.tx.fireflyAccountId) continue

                val distance = abs(secondsDiff)
                withdrawal.consider(deposit, distance)
                deposit.consider(withdrawal, distance)
            }
        }
    }

    private fun matchCurrency(tx: PlaidFireflyTransaction): String? {
        val firefly = tx.fireflyTransaction
        val fireflyCurrency = firefly?.tx?.currencyCode?.takeUnless { it.isBlank() }
        val plaid = tx.plaidTransaction ?: return fireflyCurrency
        val plaidCurrency = (plaid.isoCurrencyCode ?: plaid.unofficialCurrencyCode)?.takeUnless { it.isBlank() }

        // The replacement retains the existing Firefly currency when this entry is already synced.
        return plaidCurrency?.takeIf { firefly == null || it == fireflyCurrency }
    }

    private fun matchAmount(tx: PlaidFireflyTransaction): BigDecimal? {
        val plaid = tx.plaidTransaction
        if (plaid != null) return plaid.amount.takeIf { it.isFinite() }?.toBigDecimal()

        val firefly = tx.fireflyTransaction?.tx ?: return null
        val amount = firefly.amount.toBigDecimalOrNull() ?: return null
        return when (firefly.type) {
            TransactionTypeProperty.withdrawal -> amount
            TransactionTypeProperty.deposit -> amount.negate()
            else -> null
        }
    }

    private data class MatchKey(val currency: String, val amount: BigDecimal)

    private class Candidate(
        val tx: PlaidFireflyTransaction,
        val isWithdrawal: Boolean,
        val timestamp: Long,
    ) {
        var closest: Candidate? = null
            private set
        private var closestDistance = Long.MAX_VALUE

        val closestTieDistance: Long?
            get() = closestDistance.takeIf { closest == null && it != Long.MAX_VALUE }

        fun consider(partner: Candidate, distance: Long) {
            if (distance < closestDistance) {
                closest = partner
                closestDistance = distance
            } else if (distance == closestDistance) {
                closest = null
            }
        }
    }
}
