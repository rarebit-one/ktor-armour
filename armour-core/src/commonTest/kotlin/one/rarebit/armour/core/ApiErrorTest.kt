package one.rarebit.armour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiErrorTest {

    @Test
    fun fromStatus_maps_400_to_BadRequest() {
        val error = ApiError.fromStatus(400)
        assertIs<ApiError.BadRequest>(error)
        assertEquals(400, error.statusCode)
    }

    @Test
    fun fromStatus_maps_401_to_Unauthorized() {
        val error = ApiError.fromStatus(401)
        assertIs<ApiError.Unauthorized>(error)
    }

    @Test
    fun fromStatus_maps_403_to_Forbidden() {
        val error = ApiError.fromStatus(403)
        assertIs<ApiError.Forbidden>(error)
    }

    @Test
    fun fromStatus_maps_404_to_NotFound() {
        val error = ApiError.fromStatus(404)
        assertIs<ApiError.NotFound>(error)
    }

    @Test
    fun fromStatus_maps_409_to_Conflict() {
        val error = ApiError.fromStatus(409)
        assertIs<ApiError.Conflict>(error)
    }

    @Test
    fun fromStatus_maps_422_to_ValidationError() {
        val error = ApiError.fromStatus(422)
        assertIs<ApiError.ValidationError>(error)
    }

    @Test
    fun fromStatus_maps_429_to_RateLimited() {
        val error = ApiError.fromStatus(429)
        assertIs<ApiError.RateLimited>(error)
    }

    @Test
    fun fromStatus_maps_500_to_Server() {
        val error = ApiError.fromStatus(500)
        assertIs<ApiError.Server>(error)
        assertEquals(500, error.statusCode)
    }

    @Test
    fun fromStatus_maps_502_to_Server() {
        val error = ApiError.fromStatus(502)
        assertIs<ApiError.Server>(error)
        assertEquals(502, error.statusCode)
    }

    @Test
    fun fromStatus_maps_unknown_code_to_Unknown() {
        val error = ApiError.fromStatus(418)
        assertIs<ApiError.Unknown>(error)
        assertEquals(418, error.statusCode)
    }

    @Test
    fun fromStatus_preserves_custom_message_and_code() {
        val error = ApiError.fromStatus(404, code = "user.not_found", message = "User 42 not found")
        assertIs<ApiError.NotFound>(error)
        assertEquals("user.not_found", error.code)
        assertEquals("User 42 not found", error.message)
    }

    @Test
    fun isRetryable_true_for_server_errors() {
        assertTrue(ApiError.Server(500).isRetryable)
        assertTrue(ApiError.Server(503).isRetryable)
    }

    @Test
    fun isRetryable_true_for_rate_limited() {
        assertTrue(ApiError.RateLimited().isRetryable)
    }

    @Test
    fun isRetryable_true_for_network_errors() {
        assertTrue(ApiError.Network(cause = RuntimeException("timeout")).isRetryable)
    }

    @Test
    fun isRetryable_false_for_client_errors() {
        assertFalse(ApiError.BadRequest().isRetryable)
        assertFalse(ApiError.Unauthorized().isRetryable)
        assertFalse(ApiError.Forbidden().isRetryable)
        assertFalse(ApiError.NotFound().isRetryable)
        assertFalse(ApiError.Conflict().isRetryable)
        assertFalse(ApiError.ValidationError().isRetryable)
        assertFalse(ApiError.Unknown(418).isRetryable)
    }

    @Test
    fun network_error_has_statusCode_zero() {
        val error = ApiError.Network()
        assertEquals(0, error.statusCode)
        assertNull(error.code)
    }

    @Test
    fun rateLimited_carries_retryAfterSeconds() {
        val error = ApiError.RateLimited(retryAfterSeconds = 30)
        assertEquals(30, error.retryAfterSeconds)
    }
}
