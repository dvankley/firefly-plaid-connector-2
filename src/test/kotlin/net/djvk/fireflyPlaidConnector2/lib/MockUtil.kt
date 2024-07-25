package net.djvk.fireflyPlaidConnector2.lib

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AboutApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.AccountsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.apis.TransactionsApi
import net.djvk.fireflyPlaidConnector2.api.firefly.models.SystemInfo
import net.djvk.fireflyPlaidConnector2.api.firefly.models.SystemInfoData
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.BodyProvider as FireflyBodyProvider
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.HttpResponse as FireflyHttpResponse
import net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper
import net.djvk.fireflyPlaidConnector2.api.plaid.apis.PlaidApi
import net.djvk.fireflyPlaidConnector2.sync.MINIMUM_FIREFLY_VERSION
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.BodyProvider as PlaidBodyProvider
import net.djvk.fireflyPlaidConnector2.api.plaid.infrastructure.HttpResponse as PlaidHttpResponse
import org.mockito.kotlin.*
import org.mockito.stubbing.Answer

val OK_RESPONSE = mock<HttpResponse> {
    on { status } doReturn HttpStatusCode.OK
    on { headers } doReturn Headers.Empty
}

private class PlaidStubbedBodyProvider<T : Any>(val responseObj: T): PlaidBodyProvider<T> {
    override suspend fun body(response: HttpResponse): T {
        return responseObj
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <V : Any> typedBody(response: HttpResponse, type: TypeInfo): V {
        return responseObj as V
    }
}

fun <T : Any> createPlaidResponse(
    response: T,
    httpResponse: HttpResponse = OK_RESPONSE,
): PlaidHttpResponse<T> {
    return PlaidHttpResponse(httpResponse, PlaidStubbedBodyProvider(response))
}

class PlaidMock {
    val api = mock<PlaidApi>()
    val wrapper = mock<PlaidApiWrapper> {
        onBlocking { executeRequest(any<suspend (PlaidApi) -> Any>(), any(), any()) } doSuspendableAnswer {
            val requestExecutor = it.getArgument(0) as suspend (PlaidApi) -> Any
            requestExecutor.invoke(api)
        }
    }
}

private class FireflyStubbedBodyProvider<T : Any>(val responseObj: T): FireflyBodyProvider<T> {
    override suspend fun body(response: HttpResponse): T {
        return responseObj
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <V : Any> typedBody(response: HttpResponse, type: TypeInfo): V {
        return responseObj as V
    }
}

fun <T : Any> createFireflyResponse(
    response: T,
    httpResponse: HttpResponse = OK_RESPONSE,
): FireflyHttpResponse<T> {
    return FireflyHttpResponse(httpResponse, FireflyStubbedBodyProvider(response))
}

class FireflyMock {
    val aboutApi = mock<AboutApi>()
    val transactionsApi = mock<TransactionsApi>()
    val accountsApi = mock<AccountsApi>()

    init {
        val systemInfoData = SystemInfoData(
            version = MINIMUM_FIREFLY_VERSION,
            apiVersion = "1.0.0",
            phpVersion = "1.0.0",
            os = "testOs",
            driver = "testDriver"
        )
        aboutApi.stub {
            onBlocking { getAbout() } doAnswer { createFireflyResponse(SystemInfo(systemInfoData)) }
        }
    }
}
