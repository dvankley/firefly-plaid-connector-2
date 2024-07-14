package net.djvk.fireflyPlaidConnector2.lib

import com.fasterxml.jackson.annotation.JsonProperty
import net.djvk.fireflyPlaidConnector2.api.plaid.models.*
import net.djvk.fireflyPlaidConnector2.transactions.FireflyAccountId
import net.djvk.fireflyPlaidConnector2.transactions.PersonalFinanceCategoryEnum
import net.djvk.fireflyPlaidConnector2.transactions.PlaidAccountId
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import net.djvk.fireflyPlaidConnector2.util.Utilities
import java.time.LocalDate
import java.time.ZoneId

val defaultLocalNow: LocalDate = LocalDate.of(2022, 9, 1)
val defaultOffsetNow = TransactionConverter.getOffsetDateTimeForDate(ZoneId.of("America/New_York"), defaultLocalNow)

object PlaidFixtures {
    val plaidIdLength = 37

    fun getPaymentTransaction(
        pendingTransactionId: String? = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        categoryId: String? = "16001000",
        category: List<String>? = listOf("Payment", "Credit Card"),
        location: Location = Location(
            address = null,
            city = null,
            region = null,
            postalCode = null,
            country = null,
            lat = null,
            lon = null,
            storeNumber = null
        ),
        paymentMeta: PaymentMeta = PaymentMeta(
            referenceNumber = null,
            ppdId = null,
            payee = null,
            byOrderOf = null,
            payer = null,
            paymentMethod = null,
            paymentProcessor = null,
            reason = null
        ),
        accountOwner: String? = null,
        name: String = "AMERICAN EXPRESS DES:ACH PMT ID : W1111 INDN:JOHN Q PUBLIC CO ID:XXXXX11111 WEB",
        accountId: String = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        amount: Double = 1111.22,
        isoCurrencyCode: String? = "USD",
        unofficialCurrencyCode: String? = null,
        date: LocalDate = defaultLocalNow,
        pending: Boolean = false,
        transactionId: String = "ccccccccccccccccccccccccccccccccccccc",
        paymentChannel: Transaction.PaymentChannel = Transaction.PaymentChannel.other,
        authorizedDate: LocalDate? = null,
        authorizedDatetime: java.time.OffsetDateTime? = null,
        datetime: java.time.OffsetDateTime? = null,
        transactionCode: TransactionCode? = null,
        transactionType: Transaction.TransactionType? = Transaction.TransactionType.special,
        originalDescription: String? = null,
        merchantName: String? = null,
        checkNumber: String? = null,
        personalFinanceCategory: PersonalFinanceCategory? = PersonalFinanceCategoryEnum.TRANSFER_OUT_ACCOUNT_TRANSFER.toPersonalFinanceCategory(),
        website: String? = null,
        logoUrl: String? = null,
        personalFinanceCategoryIconUrl: String? = null,
        counterparties: List<TransactionCounterparty>? = null,
        merchantEntityId: String? = null,
    ): Transaction {
        return Transaction(
            pendingTransactionId = pendingTransactionId,
            categoryId = categoryId,
            category = category,
            location = location,
            paymentMeta = paymentMeta,
            accountOwner = accountOwner,
            name = name,
            accountId = accountId,
            amount = amount,
            isoCurrencyCode = isoCurrencyCode,
            unofficialCurrencyCode = unofficialCurrencyCode,
            date = date,
            pending = pending,
            transactionId = transactionId,
            paymentChannel = paymentChannel,
            authorizedDate = authorizedDate,
            authorizedDatetime = authorizedDatetime,
            datetime = datetime,
            transactionCode = transactionCode,
            transactionType = transactionType,
            originalDescription = originalDescription,
            merchantName = merchantName,
            checkNumber = checkNumber,
            personalFinanceCategory = personalFinanceCategory,
            website = website,
            logoUrl = logoUrl,
            personalFinanceCategoryIconUrl = personalFinanceCategoryIconUrl,
            counterparties = counterparties,
            merchantEntityId = merchantEntityId,
        )
    }

    fun getTransferTestTransaction(
        datetime: java.time.OffsetDateTime,
        personalFinanceCategory: PersonalFinanceCategoryEnum,
        amount: Double,
        pendingTransactionId: String? = "1111111111111111111111111111111111111",
        categoryId: String? = null,
        category: List<String>? = null,
        location: Location = Location(
            address = null,
            city = null,
            region = null,
            postalCode = null,
            country = null,
            lat = null,
            lon = null,
            storeNumber = null
        ),
        paymentMeta: PaymentMeta = PaymentMeta(
            referenceNumber = null,
            ppdId = null,
            payee = null,
            byOrderOf = null,
            payer = null,
            paymentMethod = null,
            paymentProcessor = null,
            reason = null
        ),
        accountOwner: String? = null,
        name: String = "NAAAAAAME",
        accountId: String = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        isoCurrencyCode: String? = "USD",
        unofficialCurrencyCode: String? = null,
        date: LocalDate = defaultLocalNow,
        pending: Boolean = false,
        transactionId: String = Utilities.getRandomAlphabeticalString(30),
        paymentChannel: Transaction.PaymentChannel = Transaction.PaymentChannel.other,
        authorizedDate: LocalDate? = null,
        authorizedDatetime: java.time.OffsetDateTime? = null,
        transactionCode: TransactionCode? = null,
        transactionType: Transaction.TransactionType? = null,
        originalDescription: String? = null,
        merchantName: String? = null,
        checkNumber: String? = null,
    ): Transaction {
        return Transaction(
            pendingTransactionId = pendingTransactionId,
            categoryId = categoryId,
            category = category,
            location = location,
            paymentMeta = paymentMeta,
            accountOwner = accountOwner,
            name = name,
            accountId = accountId,
            amount = amount,
            isoCurrencyCode = isoCurrencyCode,
            unofficialCurrencyCode = unofficialCurrencyCode,
            date = date,
            pending = pending,
            transactionId = transactionId,
            paymentChannel = paymentChannel,
            authorizedDate = authorizedDate,
            authorizedDatetime = authorizedDatetime,
            datetime = datetime,
            transactionCode = transactionCode,
            transactionType = transactionType,
            originalDescription = originalDescription,
            merchantName = merchantName,
            checkNumber = checkNumber,
            personalFinanceCategory = personalFinanceCategory.toPersonalFinanceCategory(),
        )
    }

    fun getTransaction(
        pendingTransactionId: String? = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        categoryId: String? = null,
        category: List<String>? = null,
        location: Location = Location(
            address = null,
            city = null,
            region = null,
            postalCode = null,
            country = null,
            lat = null,
            lon = null,
            storeNumber = null
        ),
        paymentMeta: PaymentMeta = PaymentMeta(
            referenceNumber = null,
            ppdId = null,
            payee = null,
            byOrderOf = null,
            payer = null,
            paymentMethod = null,
            paymentProcessor = null,
            reason = null
        ),
        accountOwner: String? = null,
        name: String = "NAAAAAAME",
        accountId: String = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        amount: Double = 1111.22,
        isoCurrencyCode: String? = "USD",
        unofficialCurrencyCode: String? = null,
        date: LocalDate = defaultLocalNow,
        pending: Boolean = false,
        transactionId: String = "ccccccccccccccccccccccccccccccccccccc",
        paymentChannel: Transaction.PaymentChannel,
        authorizedDate: LocalDate? = null,
        authorizedDatetime: java.time.OffsetDateTime? = null,
        datetime: java.time.OffsetDateTime? = null,
        transactionCode: TransactionCode? = null,
        transactionType: Transaction.TransactionType? = null,
        originalDescription: String? = null,
        merchantName: String? = null,
        checkNumber: String? = null,
        personalFinanceCategory: PersonalFinanceCategory,
    ): Transaction {
        return Transaction(
            pendingTransactionId = pendingTransactionId,
            categoryId = categoryId,
            category = category,
            location = location,
            paymentMeta = paymentMeta,
            accountOwner = accountOwner,
            name = name,
            accountId = accountId,
            amount = amount,
            isoCurrencyCode = isoCurrencyCode,
            unofficialCurrencyCode = unofficialCurrencyCode,
            date = date,
            pending = pending,
            transactionId = transactionId,
            paymentChannel = paymentChannel,
            authorizedDate = authorizedDate,
            authorizedDatetime = authorizedDatetime,
            datetime = datetime,
            transactionCode = transactionCode,
            transactionType = transactionType,
            originalDescription = originalDescription,
            merchantName = merchantName,
            checkNumber = checkNumber,
            personalFinanceCategory = personalFinanceCategory,
        )
    }

    fun getItem(
        itemId: kotlin.String = "testItemId1",
        webhook: kotlin.String? = null,
        error: PlaidError? = null,
        availableProducts: List<Products> = listOf(Products.transactions),
        billedProducts: List<Products> = listOf(Products.transactions),
        consentExpirationTime: java.time.OffsetDateTime? = null,
        updateType: Item.UpdateType = Item.UpdateType.background,
        institutionId: String? = null,
        products: List<Products>? = null,
        consentedProducts: List<Products>? = null,
    ): Item {
        return Item(
            itemId = itemId,
            webhook = webhook,
            error = error,
            availableProducts = availableProducts,
            billedProducts = billedProducts,
            consentExpirationTime = consentExpirationTime,
            updateType = updateType,
            institutionId = institutionId,
            products = products,
            consentedProducts = consentedProducts,
        )
    }

    fun getStandardAccountMapping(): Map<PlaidAccountId, FireflyAccountId> {
        val out = mutableMapOf<PlaidAccountId, FireflyAccountId>()
        var index = 1

        for (letter in 'a'..'z') {
            val id = letter.toString().repeat(plaidIdLength)
            out[id] = index
            index++
        }
        return out
    }
}
