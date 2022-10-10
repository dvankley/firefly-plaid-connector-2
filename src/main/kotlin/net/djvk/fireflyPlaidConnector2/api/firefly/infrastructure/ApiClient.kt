package net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure


import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.jackson.*
import net.djvk.fireflyPlaidConnector2.api.firefly.auth.*

abstract class ApiClient(
    private val baseUrl: String,
    httpClientEngine: HttpClientEngine?,
    httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
    jsonBlock: ObjectMapper.() -> Unit = JSON_DEFAULT,
) {

    private val clientConfig: (HttpClientConfig<*>) -> Unit by lazy {
        {
            it.install(ContentNegotiation) {
                jackson { jsonBlock() }
            }
            httpClientConfig?.invoke(it)
        }
    }

    private val client: HttpClient by lazy {
        httpClientEngine?.let { HttpClient(it, clientConfig) } ?: HttpClient(clientConfig)
    }

    private val authentications: Map<String, Authentication> by lazy {
        mapOf(
            "firefly_iii_auth" to OAuth(),
            "local_bearer_auth" to HttpBearerAuth("bearer")
        )
    }

    companion object {
        const val BASE_URL = "https://demo.firefly-iii.org"
        val JSON_DEFAULT: ObjectMapper.() -> Unit = {
            configure(SerializationFeature.INDENT_OUTPUT, true)
            setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            })
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
        protected val UNSAFE_HEADERS = listOf<String>()
    }

    /**
     * Set the username for the first HTTP basic authentication.
     *
     * @param username Username
     */
    fun setUsername(username: String) {
        val auth = authentications.values.firstOrNull { it is HttpBasicAuth } as HttpBasicAuth?
            ?: throw Exception("No HTTP basic authentication configured")
        auth.username = username
    }

    /**
     * Set the password for the first HTTP basic authentication.
     *
     * @param password Password
     */
    fun setPassword(password: String) {
        val auth = authentications.values.firstOrNull { it is HttpBasicAuth } as HttpBasicAuth?
            ?: throw Exception("No HTTP basic authentication configured")
        auth.password = password
    }

    /**
     * Set the API key value for the first API key authentication.
     *
     * @param apiKey API key
     * @param paramName The name of the API key parameter, or null or set the first key.
     */
    fun setApiKey(apiKey: String, paramName: String? = null) {
        val auth =
            authentications.values.firstOrNull { it is ApiKeyAuth && (paramName == null || paramName == it.paramName) } as ApiKeyAuth?
                ?: throw Exception("No API key authentication configured")
        auth.apiKey = apiKey
    }

    /**
     * Set the API key prefix for the first API key authentication.
     *
     * @param apiKeyPrefix API key prefix
     * @param paramName The name of the API key parameter, or null or set the first key.
     */
    fun setApiKeyPrefix(apiKeyPrefix: String, paramName: String? = null) {
        val auth =
            authentications.values.firstOrNull { it is ApiKeyAuth && (paramName == null || paramName == it.paramName) } as ApiKeyAuth?
                ?: throw Exception("No API key authentication configured")
        auth.apiKeyPrefix = apiKeyPrefix
    }

    /**
     * Set the access token for the first OAuth2 authentication.
     *
     * @param accessToken Access token
     */
    fun setAccessToken(accessToken: String) {
        val auth = authentications.values.firstOrNull { it is OAuth } as OAuth?
            ?: throw Exception("No OAuth2 authentication configured")
        auth.accessToken = accessToken
    }

    /**
     * Set the access token for the first Bearer authentication.
     *
     * @param bearerToken The bearer token.
     */
    fun setBearerToken(bearerToken: String) {
        val auth = authentications.values.firstOrNull { it is HttpBearerAuth } as HttpBearerAuth?
            ?: throw Exception("No Bearer authentication configured")
        auth.bearerToken = bearerToken
    }

    protected suspend fun <T : Any?> multipartFormRequest(
        requestConfig: RequestConfig<T>,
        body: List<PartData>?,
        authNames: List<String>
    ): HttpResponse {
        return request(requestConfig, MultiPartFormDataContent(body ?: listOf()), authNames)
    }

    protected suspend fun <T : Any?> urlEncodedFormRequest(
        requestConfig: RequestConfig<T>,
        body: Parameters?,
        authNames: List<String>
    ): HttpResponse {
        return request(requestConfig, FormDataContent(body ?: Parameters.Empty), authNames)
    }

    protected suspend fun <T : Any?> jsonRequest(
        requestConfig: RequestConfig<T>,
        body: Any? = null,
        authNames: List<String>
    ): HttpResponse {
        requestConfig.headers[HttpHeaders.ContentType] = ContentType.Application.Json.toString()
        return request(requestConfig, body, authNames)
    }

    protected suspend fun <T : Any?> request(
        requestConfig: RequestConfig<T>,
        body: Any? = null,
        authNames: List<String>
    ): HttpResponse {
        requestConfig.updateForAuth<T>(authNames)
        val headers = requestConfig.headers

        return client.request {
            this.url {
                this.takeFrom(URLBuilder(baseUrl))
                appendPath(requestConfig.path.trimStart('/').split('/'))
                requestConfig.query.forEach { query ->
                    query.value.forEach { value ->
                        parameter(query.key, value)
                    }
                }
            }
            this.method = requestConfig.method.httpMethod
            headers.filter { header -> !UNSAFE_HEADERS.contains(header.key) }
                .forEach { header -> this.header(header.key, header.value) }
            if (requestConfig.method in listOf(RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH))
                setBody(body)
        }
    }

    private fun <T : Any?> RequestConfig<T>.updateForAuth(authNames: List<String>) {
        for (authName in authNames) {
            val auth = authentications.get(authName) ?: throw Exception("Authentication undefined: $authName")
            auth.apply(query, headers)
        }
    }

    private fun URLBuilder.appendPath(components: List<String>): URLBuilder = apply {
        encodedPath =
            encodedPath.trimEnd('/') + components.joinToString("/", prefix = "/") { it.encodeURLQueryComponent() }
    }

    private val RequestMethod.httpMethod: HttpMethod
        get() = when (this) {
            RequestMethod.DELETE -> HttpMethod.Delete
            RequestMethod.GET -> HttpMethod.Get
            RequestMethod.HEAD -> HttpMethod.Head
            RequestMethod.PATCH -> HttpMethod.Patch
            RequestMethod.PUT -> HttpMethod.Put
            RequestMethod.POST -> HttpMethod.Post
            RequestMethod.OPTIONS -> HttpMethod.Options
        }
}
