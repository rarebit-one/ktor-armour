package one.rarebit.armour.core

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Wraps a Ktor HTTP call, mapping responses and exceptions into a typed [Result].
 *
 * - Successful responses are deserialized to [T].
 * - Client errors (4xx) are parsed into the matching [ApiError] subclass.
 * - Server errors (5xx) become [ApiError.Server].
 * - Network/IO exceptions become [ApiError.Network].
 *
 * ```kotlin
 * val result: Result<UserDto> = safeApiCall { client.get("/users/me") }
 * result.onSuccess { user -> … }
 *        .onFailure { error -> … }
 * ```
 */
suspend inline fun <reified T> safeApiCall(
    json: Json = DefaultJson,
    crossinline block: suspend () -> HttpResponse,
): Result<T> =
    try {
        val response = block()
        if (response.status.isSuccess()) {
            Result.success(response.body<T>())
        } else {
            Result.failure(response.toApiError(json))
        }
    } catch (e: ApiError) {
        Result.failure(e)
    } catch (e: ClientRequestException) {
        Result.failure(e.response.toApiError(json))
    } catch (e: ServerResponseException) {
        Result.failure(
            ApiError.Server(
                statusCode = e.response.status.value,
                message = "Server error (${e.response.status.value})",
                cause = e,
            ),
        )
    } catch (e: Exception) {
        Result.failure(
            ApiError.Network(
                message = e.message ?: "Unknown network error",
                cause = e,
            ),
        )
    }

/**
 * Like [safeApiCall] but for endpoints that return no body (e.g. 204 No Content).
 */
suspend inline fun safeApiCallUnit(
    json: Json = DefaultJson,
    crossinline block: suspend () -> HttpResponse,
): Result<Unit> =
    try {
        val response = block()
        if (response.status.isSuccess()) {
            Result.success(Unit)
        } else {
            Result.failure(response.toApiError(json))
        }
    } catch (e: ApiError) {
        Result.failure(e)
    } catch (e: ClientRequestException) {
        Result.failure(e.response.toApiError(json))
    } catch (e: ServerResponseException) {
        Result.failure(
            ApiError.Server(
                statusCode = e.response.status.value,
                message = "Server error (${e.response.status.value})",
                cause = e,
            ),
        )
    } catch (e: Exception) {
        Result.failure(
            ApiError.Network(
                message = e.message ?: "Unknown network error",
                cause = e,
            ),
        )
    }

/** Parse an HTTP error response into the appropriate [ApiError]. */
@PublishedApi
internal suspend fun HttpResponse.toApiError(json: Json = DefaultJson): ApiError {
    val body = try {
        val text = bodyAsText()
        json.decodeFromString<ApiErrorResponse>(text)
    } catch (_: Exception) {
        null
    }

    val retryAfter = headers["Retry-After"]?.toIntOrNull()

    return when (status.value) {
        429 -> ApiError.RateLimited(
            code = body?.error?.code,
            message = body?.error?.message ?: "Too many requests. Please wait and try again.",
            details = body?.error?.details,
            retryAfterSeconds = retryAfter,
        )
        else -> ApiError.fromStatus(
            statusCode = status.value,
            code = body?.error?.code,
            message = body?.error?.message,
            details = body?.error?.details,
        )
    }
}

@PublishedApi
internal val DefaultJson = Json { ignoreUnknownKeys = true }
