package net.djvk.fireflyPlaidConnector2.config

import net.djvk.fireflyPlaidConnector2.sync.BatchSyncRunner
import net.djvk.fireflyPlaidConnector2.sync.CursorManager
import net.djvk.fireflyPlaidConnector2.sync.FireflyTransactionService
import net.djvk.fireflyPlaidConnector2.sync.PlaidCorrectionQueue
import net.djvk.fireflyPlaidConnector2.sync.PlaidSyncService
import net.djvk.fireflyPlaidConnector2.sync.PolledSyncOrchestrator
import net.djvk.fireflyPlaidConnector2.sync.TransferReplacementJournal
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["fireflyPlaidConnector2.syncMode"], havingValue = "batch")
@EnableConfigurationProperties(BatchSyncProperties::class)
@Import(BatchSyncRunner::class)
class BatchSyncConfiguration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["fireflyPlaidConnector2.syncMode"], havingValue = "polled")
@EnableConfigurationProperties(PolledModeProperties::class)
@Import(
    PolledSyncOrchestrator::class,
    PlaidSyncService::class,
    FireflyTransactionService::class,
    TransferReplacementJournal::class,
    PlaidCorrectionQueue::class,
)
class PolledSyncConfiguration {
    @Bean
    fun polledSyncProperties(properties: PolledModeProperties): PolledSyncProperties = properties.polled

    @Bean
    fun cursorManager(properties: PolledSyncProperties): CursorManager =
        CursorManager(properties.cursorFileDirectoryPath)
}
