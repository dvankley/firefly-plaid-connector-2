package net.djvk.fireflyPlaidConnector2.api

import com.fasterxml.jackson.core.JsonProcessingException
import io.ktor.client.engine.cio.FailToConnectException
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

internal enum class ApiService(val displayName: String) {
    PLAID("Plaid"),
    FIREFLY("Firefly"),
}

internal class ApiTransportException(
    val service: ApiService,
    val operation: String,
    cause: Exception,
) : RuntimeException("${service.displayName} transport failure during $operation", cause)

// Scope IOException handling to HTTP calls and their response bodies. Cursor and journal I/O
// is performed outside these blocks.
internal suspend fun <T> ApiService.withTransportFailure(operation: String, request: suspend () -> T): T {
    try {
        return request()
    } catch (failure: Exception) {
        // CIO uses this internal IOException subtype for TLS negotiation and hostname checks.
        if (failure.javaClass.name == "io.ktor.network.tls.TLSException") throw failure
        when (failure) {
            is JsonProcessingException, is SSLException -> throw failure
            is IOException, is UnresolvedAddressException, is FailToConnectException ->
                throw ApiTransportException(this, operation, failure)
            else -> throw failure
        }
    }
}
