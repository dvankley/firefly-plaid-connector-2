package net.djvk.fireflyPlaidConnector2

import kotlinx.coroutines.runBlocking
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.ApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class BatchSyncRunner(
    @Value("\${fireflyPlaidConnector2.firefly.personalAccessToken}")
    private val fireflyAccessToken: String,

    private val fireflyApi: AboutApi,
) : Runner {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun run() {
        runBlocking {
            fireflyApi.setAccessToken(fireflyAccessToken)
            val about = fireflyApi.getAbout().body()
            logger.debug("About: " + about)
        }
    }
}