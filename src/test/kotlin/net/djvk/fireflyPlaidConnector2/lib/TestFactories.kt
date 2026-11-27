package net.djvk.fireflyPlaidConnector2.lib

import net.djvk.fireflyPlaidConnector2.config.properties.CategorizationProperties
import net.djvk.fireflyPlaidConnector2.config.properties.ConnectorProperties
import net.djvk.fireflyPlaidConnector2.config.properties.FireflyApiProperties
import net.djvk.fireflyPlaidConnector2.config.properties.TransactionStyleConfig
import net.djvk.fireflyPlaidConnector2.transactions.TransactionConverter
import net.djvk.fireflyPlaidConnector2.transactions.TransferMatcher

fun randomAlphabeticalString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

fun testFireflyProperties(personalAccessToken: String = "testToken") =
    FireflyApiProperties("https://firefly.test", personalAccessToken)

fun testTransferMatcher(timeZone: String = "America/New_York", transferMatchWindowDays: Long) =
    TransferMatcher(ConnectorProperties(timeZone, transferMatchWindowDays))

/**
 * Builds a [TransactionConverter] (and the [TransferMatcher] it depends on) from flat settings.
 */
fun testTransactionConverter(
    useNameForDestination: Boolean,
    timeZoneString: String,
    transferMatchWindowDays: Long,
    enablePrimaryCategorization: Boolean,
    primaryCategoryPrefix: String,
    enableDetailedCategorization: Boolean,
    detailedCategoryPrefix: String,
    txStyle: TransactionStyleConfig,
): TransactionConverter {
    val connector = ConnectorProperties(timeZoneString, transferMatchWindowDays, useNameForDestination)
    return TransactionConverter(
        connector,
        CategorizationProperties(
            CategorizationProperties.Primary(enablePrimaryCategorization, primaryCategoryPrefix),
            CategorizationProperties.Detailed(enableDetailedCategorization, detailedCategoryPrefix),
        ),
        txStyle,
        TransferMatcher(connector),
    )
}
