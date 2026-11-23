package net.djvk.fireflyPlaidConnector2.lib

import net.djvk.fireflyPlaidConnector2.config.PolledSyncProperties
import java.time.LocalDate

fun testPolledProperties(
    syncFrequencyMinutes: Int = 30,
    existingFireflyPullWindowDays: Int = 30,
    allowItemToFail: Boolean = false,
    initialBackfillAfter: LocalDate? = null,
    initialBackfillThrough: LocalDate? = null,
) = PolledSyncProperties(
    syncFrequencyMinutes = syncFrequencyMinutes,
    existingFireflyPullWindowDays = existingFireflyPullWindowDays,
    cursorFileDirectoryPath = "persistence/",
    allowItemToFail = allowItemToFail,
    initialBackfillAfter = initialBackfillAfter,
    initialBackfillThrough = initialBackfillThrough,
)
