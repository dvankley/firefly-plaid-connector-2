package net.djvk.fireflyPlaidConnector2.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

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