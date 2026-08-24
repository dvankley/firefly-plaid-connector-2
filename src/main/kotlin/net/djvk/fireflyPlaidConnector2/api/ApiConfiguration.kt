package net.djvk.fireflyPlaidConnector2.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Profile("!test")
@Configuration
class ApiConfiguration {
    @Bean
    fun getEngine(): HttpClientEngine {
        return CIO.create()
    }

    /**
     * Marked [Primary] so the many unused/unqualified Firefly OpenAPI-generated client classes
     *  (which also take an `httpClientConfig` constructor param but aren't explicitly qualified)
     *  keep resolving to this bean unambiguously, same as before this bean was split in two.
     */
    @Bean
    @Primary
    @Qualifier("plaidClientConfig")
    fun getClientConfig(): ((HttpClientConfig<*>) -> Unit) {
        return {
            it.expectSuccess = true
            it.install(HttpTimeout) {
                /**
                 * This is high enough for Plaid's /accounts/balance/get endpoint to do whatever synchronous shenanigans
                 *  it wants to and return something useful rather than our client just timing out
                 */
                requestTimeoutMillis = 60000
            }
//            it.install(Logging) {
//                level = LogLevel.ALL
//            }
        }
    }

    @Bean
    @Qualifier("fireflyClientConfig")
    fun getFireflyClientConfig(
        @Value("\${fireflyPlaidConnector2.firefly.requestTimeoutMillis:60000}")
        requestTimeoutMillis: Long,
    ): ((HttpClientConfig<*>) -> Unit) {
        return {
            it.expectSuccess = true
            it.install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
    }

//    @Bean
//    fun getJsonBlock(): ObjectMapper.() -> Unit {
//        return {}
//    }
}