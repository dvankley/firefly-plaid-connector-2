package net.djvk.fireflyPlaidConnector2.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject

import kotlinx.serialization.json.putJsonObject
import net.djvk.fireflyPlaidConnector2.api.firefly.models.TransactionRead

import net.djvk.fireflyPlaidConnector2.constants.FireflyTransactionId
import net.djvk.fireflyPlaidConnector2.transactions.FireflyTransactionDto


import org.springframework.boot.SpringBootVersion
import org.springframework.boot.system.SystemProperties
import org.springframework.core.SpringVersion
import org.springframework.stereotype.Component

import net.djvk.fireflyPlaidConnector2.constants.ResultCallbackUrl
import net.djvk.fireflyPlaidConnector2.constants.ResultCallbackBearerToken
import java.time.Duration

@Component
class WebhookService (
    private val resultCallbackUrl: ResultCallbackUrl,
    private val resultCallbackBearerToken: ResultCallbackBearerToken
 )  {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private var lastJsonData: JsonElement? = null
    private var webhookClient: HttpClient? = null
    init{
        logger.trace("Init Webhook Client")

        if  (!resultCallbackBearerToken.isNullOrBlank()){
            webhookClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                        isLenient = true
                    })
                }
            }
            logger.debug("Created Webhook Client for $resultCallbackUrl")
        }
    }

    fun addDataForHook(ffTx: List<TransactionRead>,
                       ptr: PlaidTransactionResult,
                       creates: List<FireflyTransactionDto>,
                       updates: List<FireflyTransactionDto>,
                       deletes: List<FireflyTransactionId>){
        if (lastJsonData != null){
            logger.warn("Data for Webhook provided, but will be overwritten before sent")
        }else{
            logger.debug("Adding some data for callback")
        }

        lastJsonData = buildJsonObject {
            put("fireFlyTransactionsRead", JsonPrimitive(ffTx.size))
            putJsonObject("plaidTransactions") {
                put("n_created", JsonPrimitive(ptr.created.size))
                put("n_updated", JsonPrimitive(ptr.updated.size))
                put("n_deleted", JsonPrimitive(ptr.deleted.size))
            }
            putJsonObject("fireFlyTransactionConversions") {
                put("n_created", JsonPrimitive(creates.size))
                put("n_updated", JsonPrimitive(updates.size))
                put("n_deleted", JsonPrimitive(deletes.size))
            }
        }
    }

    fun enabled() : Boolean{
        webhookClient?.let{
            return true
        }
        return false
    }

    suspend fun post(
        loopDuration: Duration){

        logger.info("Sending results to $resultCallbackUrl")

        if (lastJsonData == null){
            logger.error("Cannot send empty webhook data")
            return
        }

        val toSend = buildJsonObject {
            put("loopDurationSecs", JsonPrimitive(loopDuration.toMillis()/1000.0))
            put( "springVersion", JsonPrimitive(SpringVersion.getVersion()))
            put( "springBootVersion", JsonPrimitive(SpringBootVersion.getVersion()))
            put("javaVersion",  JsonPrimitive(SystemProperties.get("java.version")))

            // Would love to add the appVersion
            //put("connectorVersion", JsonPrimitive(environment.getProperty("build.version", "unknown")))

            put("results", lastJsonData!!)
        }


        webhookClient?.use { webhookClient ->
            val response: HttpResponse = webhookClient.post("$resultCallbackUrl") {

                if (!resultCallbackBearerToken.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $resultCallbackBearerToken")

                }
                contentType(ContentType.Application.Json)
                setBody(toSend)
            }

            logger.info("Webhook Response (${response.status}): ${response.bodyAsText()}")
        } // HttpClient is closed here automatically
        lastJsonData = null
    }
}
