package net.djvk.fireflyPlaidConnector2.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.DateTimeException
import java.time.ZoneId

/**
 * Top-level settings shared by transaction conversion and transfer matching.
 */
@ConfigurationProperties(prefix = "firefly-plaid-connector2")
data class ConnectorProperties(
    val timeZone: String,
    val transferMatchWindowDays: Long,
    val useNameForDestination: Boolean = true,
) {
    val zoneId: ZoneId = try {
        ZoneId.of(timeZone)
    } catch (e: DateTimeException) {
        throw IllegalArgumentException("fireflyPlaidConnector2.timeZone is not a valid time zone ID: $timeZone", e)
    }
}
