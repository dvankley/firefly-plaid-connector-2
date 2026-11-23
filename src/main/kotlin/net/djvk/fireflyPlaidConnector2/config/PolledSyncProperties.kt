package net.djvk.fireflyPlaidConnector2.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

// Both properties are bound together so the polled lookback can be checked against the shared matching window.
@ConfigurationProperties(prefix = "firefly-plaid-connector2")
data class PolledModeProperties(
    val transferMatchWindowDays: Long,
    val polled: PolledSyncProperties,
) {
    init {
        require(polled.existingFireflyPullWindowDays > transferMatchWindowDays) {
            "fireflyPlaidConnector2.polled.existingFireflyPullWindowDays must exceed transferMatchWindowDays"
        }
    }
}

data class PolledSyncProperties(
    val syncFrequencyMinutes: Int,
    val existingFireflyPullWindowDays: Int,
    val cursorFileDirectoryPath: String,
    val allowItemToFail: Boolean = false,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val initialBackfillAfter: LocalDate? = null,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val initialBackfillThrough: LocalDate? = null,
) {
    init {
        require(syncFrequencyMinutes > 0) { "fireflyPlaidConnector2.polled.syncFrequencyMinutes must be positive" }
        require(existingFireflyPullWindowDays >= 0) {
            "fireflyPlaidConnector2.polled.existingFireflyPullWindowDays must be nonnegative"
        }
        if (initialBackfillThrough != null) {
            require(initialBackfillAfter != null) {
                "fireflyPlaidConnector2.polled.initialBackfillThrough requires initialBackfillAfter"
            }
            require(initialBackfillThrough > initialBackfillAfter) {
                "fireflyPlaidConnector2.polled.initialBackfillThrough must be later than initialBackfillAfter"
            }
        }
    }
}
