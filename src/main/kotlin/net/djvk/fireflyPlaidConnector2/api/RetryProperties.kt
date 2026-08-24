package net.djvk.fireflyPlaidConnector2.api

/**
 * Configuration for [RetryExecutor]. The same shape is bound at multiple config prefixes
 * (e.g. one for Plaid, one for Firefly) so the two can be tuned independently while sharing
 * the same knob names.
 */
data class RetryProperties(
    /**
     * Total number of attempts, including the first, before giving up and letting the
     * exception propagate. Null means "use [RetryExecutor.DEFAULT_MAX_ATTEMPTS]" unless a
     * caller-specific fallback applies (see [net.djvk.fireflyPlaidConnector2.api.plaid.PlaidApiWrapper]'s
     * deprecated `maxRetries` inference).
     */
    var maxAttempts: Int? = null,
    var initialDelayMs: Long = 1000,
    var maxDelayMs: Long = 60000,
    var multiplier: Double = 2.0,
    var jitterFactor: Double = 0.2,
)
