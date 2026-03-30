package one.rarebit.armour.retry

import kotlinx.coroutines.delay
import one.rarebit.armour.core.ApiError

/**
 * Executes a suspending [block] with automatic retries on transient failures.
 *
 * Only retries errors where [ApiError.isRetryable] is true (server errors, rate limits,
 * network errors). Client errors (4xx) fail immediately.
 *
 * Uses exponential backoff with optional jitter. When a [ApiError.RateLimited] error
 * includes a `retryAfterSeconds` hint, that value is used instead of the computed delay.
 *
 * ```kotlin
 * val policy = RetryPolicy(maxRetries = 3)
 * val users = policy.execute { api.getUsers() }
 * ```
 */
class RetryPolicy(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 10_000,
    private val backoffMultiplier: Double = 2.0,
    private val jitter: Boolean = true,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(initialDelayMs > 0) { "initialDelayMs must be > 0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs must be >= initialDelayMs" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
    }

    /**
     * Execute [block] with retry logic. Returns the result of the first successful attempt.
     *
     * @throws ApiError if all retries are exhausted or the error is not retryable.
     * @throws Exception if a non-[ApiError] exception occurs on the final attempt.
     */
    suspend fun <T> execute(block: suspend (attempt: Int) -> T): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                return block(attempt)
            } catch (e: ApiError) {
                lastException = e
                if (!e.isRetryable || attempt == maxRetries) throw e

                val retryAfter = (e as? ApiError.RateLimited)?.retryAfterSeconds
                val delayMs = if (retryAfter != null) {
                    retryAfter.toLong() * 1000
                } else {
                    applyJitter(currentDelay)
                }

                delay(delayMs)
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            } catch (e: Exception) {
                lastException = e
                if (attempt == maxRetries) throw e

                delay(applyJitter(currentDelay))
                currentDelay = (currentDelay * backoffMultiplier).toLong().coerceAtMost(maxDelayMs)
            }
        }

        throw lastException ?: IllegalStateException("Retry exhausted with no exception")
    }

    private fun applyJitter(delayMs: Long): Long {
        if (!jitter) return delayMs
        val jitterRange = (delayMs * 0.2).toLong().coerceAtLeast(1)
        return delayMs + (-jitterRange..jitterRange).random()
    }
}
