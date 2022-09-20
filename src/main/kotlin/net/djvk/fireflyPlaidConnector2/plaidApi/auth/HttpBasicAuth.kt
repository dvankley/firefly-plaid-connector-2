package net.djvk.fireflyPlaidConnector2.plaidApi.auth

import io.ktor.util.*

class HttpBasicAuth : Authentication {
    var username: String? = null
    var password: String? = null

    @OptIn(InternalAPI::class)
    override fun apply(query: MutableMap<String, List<String>>, headers: MutableMap<String, String>) {
        if (username == null && password == null) return
        val str = (username ?: "") + ":" + (password ?: "")
        val auth = str.encodeBase64()
        headers["Authorization"] = "Basic $auth"
    }
}
