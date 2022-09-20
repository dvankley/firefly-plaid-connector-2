package net.djvk.fireflyPlaidConnector2.api

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
class ApiConfiguration {
    @Bean
    fun getEngine(): HttpClientEngine {
        return CIO.create()
    }

    @Bean
    fun getClientConfig(): ((HttpClientConfig<*>) -> Unit) {
        return {}
    }

    @Bean
    fun getJsonBlock(): ObjectMapper.() -> Unit {
        return {}
    }
}