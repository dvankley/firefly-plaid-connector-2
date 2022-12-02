package net.djvk.fireflyPlaidConnector2.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Profile("!test")
@Configuration
class ApiConfiguration {
    @Bean
    fun getEngine(): HttpClientEngine {
        return CIO.create()
    }

    @Bean
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

//    @Bean
//    fun getJsonBlock(): ObjectMapper.() -> Unit {
//        return {}
//    }
}