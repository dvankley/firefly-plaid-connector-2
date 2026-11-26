package net.djvk.fireflyPlaidConnector2.api.firefly.auth

import java.util.Base64

class HttpBasicAuth : Authentication {
    var username: String? = null
    var password: String? = null

    override fun apply(query: MutableMap<String, List<String>>, headers: MutableMap<String, String>) {
        if (username == null && password == null) return
        val str = (username ?: "") + ":" + (password ?: "")
        val auth = Base64.getEncoder().encodeToString(str.toByteArray())
        headers["Authorization"] = "Basic $auth"
    }
}
