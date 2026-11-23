package net.djvk.fireflyPlaidConnector2.config

import net.djvk.fireflyPlaidConnector2.constants.IntervalSeconds
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@ConfigurationProperties(prefix = "firefly-plaid-connector2.batch")
data class BatchSyncProperties(
    val maxSyncDays: Int,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val endDate: LocalDate? = null,
    val setInitialBalance: Boolean = false,
    val balanceMinLastUpdatedDatetimeSeconds: IntervalSeconds? = null,
) {
    init {
        require(maxSyncDays >= 0) { "fireflyPlaidConnector2.batch.maxSyncDays must be nonnegative" }
    }
}
