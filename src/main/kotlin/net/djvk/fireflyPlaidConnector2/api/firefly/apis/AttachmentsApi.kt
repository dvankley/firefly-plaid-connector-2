/**
 * Firefly III API v1.5.6
 *
 * This is the documentation of the Firefly III API. You can find accompanying documentation on the website of Firefly III itself (see below). Please report any bugs or issues. You may use the \"Authorize\" button to try the API below. This file was last generated on 2022-04-04T03:54:41+00:00
 *
 * The version of the OpenAPI document: 1.5.6
 * Contact: james@firefly-iii.org
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package net.djvk.fireflyPlaidConnector2.api.firefly.apis

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.*
import net.djvk.fireflyPlaidConnector2.api.firefly.infrastructure.*
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AttachmentArray
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AttachmentSingle
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AttachmentStore
import net.djvk.fireflyPlaidConnector2.api.firefly.models.AttachmentUpdate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
open class AttachmentsApi(
    @Value("\${fireflyPlaidConnector2.firefly.url}")
    baseUrl: String = ApiClient.BASE_URL,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: ((HttpClientConfig<*>) -> Unit)? = null,
    jsonBlock: ObjectMapper.() -> Unit = ApiClient.JSON_DEFAULT,
) : ApiClient(baseUrl, httpClientEngine, httpClientConfig, jsonBlock) {

    /**
     * Delete an attachment.
     * With this endpoint you delete an attachment, including any stored file data.
     * @param id The ID of the single.
     * @return void
     */
    open suspend fun deleteAttachment(id: kotlin.String): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.DELETE,
            "/api/v1/attachments/{id}".replace("{" + "id" + "}", "$id"),
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Download a single attachment.
     * This endpoint allows you to download the binary content of a transaction. It will be sent to you as a download, using the content type \&quot;application/octet-stream\&quot; and content disposition \&quot;attachment; filename&#x3D;example.pdf\&quot;.
     * @param id The ID of the attachment.
     * @return java.io.File
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun downloadAttachment(id: kotlin.String): HttpResponse<java.io.File> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/api/v1/attachments/{id}/download".replace("{" + "id" + "}", "$id"),
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Get a single attachment.
     * Get a single attachment. This endpoint only returns the available metadata for the attachment. Actual file data is handled in two other endpoints (see below).
     * @param id The ID of the attachment.
     * @return AttachmentSingle
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun getAttachment(id: kotlin.String): HttpResponse<AttachmentSingle> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/api/v1/attachments/{id}".replace("{" + "id" + "}", "$id"),
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * List all attachments.
     * This endpoint lists all attachments.
     * @param page Page number. The default pagination is 50. (optional)
     * @return AttachmentArray
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun listAttachment(page: kotlin.Int?): HttpResponse<AttachmentArray> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody =
            io.ktor.client.utils.EmptyContent

        val localVariableQuery = mutableMapOf<String, List<String>>()
        page?.apply { localVariableQuery["page"] = listOf("$page") }

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.GET,
            "/api/v1/attachments",
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return request(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Store a new attachment.
     * Creates a new attachment. The data required can be submitted as a JSON body or as a list of parameters. You cannot use this endpoint to upload the actual file data (see below). This endpoint only creates the attachment object.
     * @param attachmentStore JSON array or key&#x3D;value pairs with the necessary attachment information. See the model for the exact specifications.
     * @return AttachmentSingle
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun storeAttachment(attachmentStore: AttachmentStore): HttpResponse<AttachmentSingle> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody = attachmentStore

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/api/v1/attachments",
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Update existing attachment.
     * Update the meta data for an existing attachment. This endpoint does not allow you to upload or download data. For that, see below.
     * @param id The ID of the attachment.
     * @param attachmentUpdate JSON array with updated attachment information. See the model for the exact specifications.
     * @return AttachmentSingle
     */
    @Suppress("UNCHECKED_CAST")
    open suspend fun updateAttachment(
        id: kotlin.String,
        attachmentUpdate: AttachmentUpdate
    ): HttpResponse<AttachmentSingle> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody = attachmentUpdate

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.PUT,
            "/api/v1/attachments/{id}".replace("{" + "id" + "}", "$id"),
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

    /**
     * Upload an attachment.
     * Use this endpoint to upload (and possible overwrite) the file contents of an attachment. Simply put the entire file in the body as binary data.
     * @param id The ID of the attachment.
     * @param body  (optional)
     * @return void
     */
    open suspend fun uploadAttachment(id: kotlin.String, body: java.io.File?): HttpResponse<Unit> {

        val localVariableAuthNames = listOf<String>("firefly_iii_auth")

        val localVariableBody = body

        val localVariableQuery = mutableMapOf<String, List<String>>()

        val localVariableHeaders = mutableMapOf<String, String>()

        val localVariableConfig = RequestConfig<kotlin.Any?>(
            RequestMethod.POST,
            "/api/v1/attachments/{id}/upload".replace("{" + "id" + "}", "$id"),
            query = localVariableQuery,
            headers = localVariableHeaders
        )

        return jsonRequest(
            localVariableConfig,
            localVariableBody,
            localVariableAuthNames
        ).wrap()
    }

}
