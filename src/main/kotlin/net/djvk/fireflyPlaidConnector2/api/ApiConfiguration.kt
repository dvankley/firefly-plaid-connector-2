package net.djvk.fireflyPlaidConnector2.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import net.djvk.fireflyPlaidConnector2.config.properties.FireflyApiProperties
import org.springframework.beans.factory.annotation.Qualifier
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
            it.defaultRequest {
                // Preserve the charset header the previous Plaid client sent by default.
                @Suppress("DEPRECATION")
                header(HttpHeaders.AcceptCharset, Charsets.UTF_8.name())
            }
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
    fun getFireflyClientConfig(properties: FireflyApiProperties): ((HttpClientConfig<*>) -> Unit) =
        fireflyClientConfig(properties.requestTimeoutMillis)

    companion object {
        fun fireflyClientConfig(requestTimeoutMillis: Long): ((HttpClientConfig<*>) -> Unit) {
            return {
                it.expectSuccess = true
                it.install(HttpTimeout) {
                    this.requestTimeoutMillis = requestTimeoutMillis
                }
            }
        }
    }

//    @Bean
//    fun getJsonBlock(): ObjectMapper.() -> Unit {
//        return {}
//    }
}
