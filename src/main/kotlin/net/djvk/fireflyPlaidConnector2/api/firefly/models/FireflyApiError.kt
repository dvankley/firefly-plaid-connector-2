package net.djvk.fireflyPlaidConnector2.api.firefly.models

data class FireflyApiError(
    val message: String,
    val errors: Map<String, List<String>>
)
