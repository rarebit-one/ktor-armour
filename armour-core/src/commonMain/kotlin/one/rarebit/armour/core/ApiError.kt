package one.rarebit.armour.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Sealed hierarchy mapping HTTP status codes to typed errors.
 *
 * Each subclass carries the original [statusCode], an optional machine-readable [code],
 * a human-readable [message], and optional structured [details].
 */
sealed class ApiError(
    open val statusCode: Int,
    open val code: String? = null,
    override val message: String,
    open val details: JsonObject? = null,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    class BadRequest(
        override val code: String? = null,
        override val message: String = "Invalid request. Please check your input.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(400, code, message, details, cause)

    class Unauthorized(
        override val code: String? = null,
        override val message: String = "Authentication required.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(401, code, message, details, cause)

    class Forbidden(
        override val code: String? = null,
        override val message: String = "Access denied.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(403, code, message, details, cause)

    class NotFound(
        override val code: String? = null,
        override val message: String = "Resource not found.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(404, code, message, details, cause)

    class Conflict(
        override val code: String? = null,
        override val message: String = "Conflict. This resource already exists or is in an invalid state.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(409, code, message, details, cause)

    class ValidationError(
        override val code: String? = null,
        override val message: String = "Validation failed. Please check your input.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(422, code, message, details, cause)

    class RateLimited(
        override val code: String? = null,
        override val message: String = "Too many requests. Please wait and try again.",
        override val details: JsonObject? = null,
        val retryAfterSeconds: Int? = null,
        override val cause: Throwable? = null,
    ) : ApiError(429, code, message, details, cause)

    class Server(
        override val statusCode: Int,
        override val code: String? = null,
        override val message: String = "Server error. Please try again later.",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(statusCode, code, message, details, cause)

    class Network(
        override val message: String = "Network error. Please check your connection.",
        override val cause: Throwable? = null,
    ) : ApiError(0, null, message, null, cause)

    class Unknown(
        override val statusCode: Int,
        override val code: String? = null,
        override val message: String = "Unexpected error (HTTP $statusCode).",
        override val details: JsonObject? = null,
        override val cause: Throwable? = null,
    ) : ApiError(statusCode, code, message, details, cause)

    /** Whether this error represents a transient failure worth retrying. */
    val isRetryable: Boolean
        get() = when (this) {
            is Server, is RateLimited, is Network -> true
            is BadRequest, is Unauthorized, is Forbidden, is NotFound,
            is Conflict, is ValidationError, is Unknown,
            -> false
        }

    companion object {
        /** Map an HTTP status code to the appropriate [ApiError] subclass. */
        fun fromStatus(
            statusCode: Int,
            code: String? = null,
            message: String? = null,
            details: JsonObject? = null,
        ): ApiError = when (statusCode) {
            400 -> BadRequest(code, message ?: "Invalid request. Please check your input.", details)
            401 -> Unauthorized(code, message ?: "Authentication required.", details)
            403 -> Forbidden(code, message ?: "Access denied.", details)
            404 -> NotFound(code, message ?: "Resource not found.", details)
            409 -> Conflict(code, message ?: "Conflict. This resource already exists or is in an invalid state.", details)
            422 -> ValidationError(code, message ?: "Validation failed. Please check your input.", details)
            429 -> RateLimited(code, message ?: "Too many requests. Please wait and try again.", details)
            in 500..599 -> Server(statusCode, code, message ?: "Server error. Please try again later.", details)
            else -> Unknown(statusCode, code, message ?: "Unexpected error (HTTP $statusCode).", details)
        }
    }
}

/** Serializable error response envelope matching common REST API conventions. */
@Serializable
data class ApiErrorResponse(val error: ApiErrorBody)

@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val details: JsonObject? = null,
)
