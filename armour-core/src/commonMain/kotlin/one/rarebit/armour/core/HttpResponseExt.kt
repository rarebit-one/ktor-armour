package one.rarebit.armour.core

import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Convert a non-success HTTP response to the appropriate [ApiError], or return null if successful.
 * Useful in token refresh handlers and other contexts where you need error classification
 * without the full safeApiCall wrapper.
 *
 * ```kotlin
 * refreshTokens {
 *     val response = client.post("v1/oauth/token") { ... }
 *     response.toApiErrorOrNull()?.let { error ->
 *         if (error is ApiError.Unauthorized) tokenStorage.clearTokens()
 *         return@refreshTokens null
 *     }
 *     // ... handle success
 * }
 * ```
 */
suspend fun HttpResponse.toApiErrorOrNull(json: Json = DefaultJson): ApiError? {
    if (status.isSuccess()) return null
    return toApiError(json)
}
