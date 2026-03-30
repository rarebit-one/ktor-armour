package one.rarebit.armour.ktor

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import one.rarebit.armour.core.ApiError
import one.rarebit.armour.core.ApiErrorBody
import one.rarebit.armour.core.ApiErrorResponse
import one.rarebit.armour.reporting.ErrorReporter
import one.rarebit.armour.reporting.NoOpReporter
import one.rarebit.armour.retry.RetryPolicy

/**
 * Configuration for the [Armour] Ktor client plugin.
 */
class ArmourConfig {
    /** Retry policy for transient failures. Set to null to disable retries. */
    var retryPolicy: RetryPolicy? = RetryPolicy()

    /** Error reporter for logging/crash reporting. */
    var reporter: ErrorReporter = NoOpReporter

    /** JSON instance for parsing error response bodies. */
    var json: Json = Json { ignoreUnknownKeys = true }

    /** HTTP status codes that should NOT be reported (e.g., 401 if you handle it via token refresh). */
    var suppressedStatusCodes: Set<Int> = emptySet()
}

/**
 * Ktor client plugin that provides unified error handling, retries, and reporting.
 *
 * Install it on your [HttpClient]:
 *
 * ```kotlin
 * val client = HttpClient {
 *     install(Armour) {
 *         retryPolicy = RetryPolicy(maxRetries = 3)
 *         reporter = mySentryReporter
 *     }
 * }
 * ```
 *
 * When installed, non-success responses are automatically converted to [ApiError] exceptions.
 * Retryable errors are retried according to the configured [RetryPolicy].
 * All errors are reported to the configured [ErrorReporter] (unless suppressed).
 */
val Armour = createClientPlugin("Armour", ::ArmourConfig) {
    val retryPolicy = pluginConfig.retryPolicy
    val reporter = pluginConfig.reporter
    val json = pluginConfig.json
    val suppressedStatusCodes = pluginConfig.suppressedStatusCodes

    on(Send) { request ->
        val call = if (retryPolicy != null) {
            retryPolicy.execute { attempt ->
                if (attempt > 0) {
                    reporter.breadcrumb("Retrying request", mapOf(
                        "attempt" to attempt.toString(),
                        "url" to request.url.toString(),
                    ))
                }

                val call = proceed(request)
                val response = call.response

                if (!response.status.isSuccess()) {
                    val apiError = parseApiError(response.status.value, response.bodyAsText(), json)
                    if (apiError.statusCode !in suppressedStatusCodes) {
                        reporter.report(apiError, mapOf("url" to request.url.toString()))
                    }
                    throw apiError
                }

                call
            }
        } else {
            val call = proceed(request)
            val response = call.response

            if (!response.status.isSuccess()) {
                val apiError = parseApiError(response.status.value, response.bodyAsText(), json)
                if (apiError.statusCode !in suppressedStatusCodes) {
                    reporter.report(apiError, mapOf("url" to request.url.toString()))
                }
                throw apiError
            }

            call
        }

        call
    }
}

private fun parseApiError(statusCode: Int, bodyText: String, json: Json): ApiError {
    val errorBody: ApiErrorBody? = try {
        json.decodeFromString<ApiErrorResponse>(bodyText).error
    } catch (_: Exception) {
        null
    }

    return when (statusCode) {
        429 -> ApiError.RateLimited(
            code = errorBody?.code,
            message = errorBody?.message ?: "Too many requests. Please wait and try again.",
            details = errorBody?.details,
        )
        else -> ApiError.fromStatus(
            statusCode = statusCode,
            code = errorBody?.code,
            message = errorBody?.message,
            details = errorBody?.details,
        )
    }
}
