package net.djvk.fireflyPlaidConnector2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import net.djvk.fireflyPlaidConnector2.api.firefly.models.SystemInfo
import net.djvk.fireflyPlaidConnector2.api.firefly.models.SystemInfoData
import net.djvk.fireflyPlaidConnector2.lib.TestApplication
import net.djvk.fireflyPlaidConnector2.sync.BatchSyncRunner
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(value = ["test"])
@SpringBootTest(
    classes = [
        TestApplication::class,
        BatchSyncRunnerTest.TestConfig::class,
    ]
)
internal class BatchSyncRunnerTest @Autowired constructor(
    val engine: MockEngine,
    val testConfig: TestConfig,
    val runner: BatchSyncRunner,
) {
    @Configuration
    internal class TestConfig {
        // Would be nice to have this as a bean instead of making it every time, but that's
        //  proven harder than expected
        private val om = jacksonObjectMapper()

        @Bean
        fun getEngine(): HttpClientEngine {
            return MockEngine { request ->
                respond(
                    content = ByteReadChannel(
                        om.writeValueAsString(
                            SystemInfo(
                                SystemInfoData(
                                    "1", "2", "3", "4", "5"
                                )
                            ),
                        ),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    }

    @Test
    fun run() {
        runner.run()
    }
}
