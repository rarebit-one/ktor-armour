package one.rarebit.armour.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
data class TestUser(val id: Int, val name: String)

class SafeApiCallTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun mockClient(
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json() }
            expectSuccess = false
        }
    }

    @Test
    fun safeApiCall_returns_success_on_200() = runTest {
        val client = mockClient { _ ->
            respond("""{"id":1,"name":"Alice"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/1") }

        assertTrue(result.isSuccess)
        assertEquals(TestUser(1, "Alice"), result.getOrThrow())
    }

    @Test
    fun safeApiCall_returns_NotFound_on_404() = runTest {
        val client = mockClient { _ ->
            respond(
                """{"error":{"code":"user.not_found","message":"User not found"}}""",
                HttpStatusCode.NotFound,
                jsonHeaders,
            )
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/999") }

        assertTrue(result.isFailure)
        val error = result.apiErrorOrNull()
        assertIs<ApiError.NotFound>(error)
        assertEquals("user.not_found", error.code)
        assertEquals("User not found", error.message)
    }

    @Test
    fun safeApiCall_returns_Unauthorized_on_401() = runTest {
        val client = mockClient { _ ->
            respond("", HttpStatusCode.Unauthorized, jsonHeaders)
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/me") }

        assertTrue(result.isFailure)
        assertIs<ApiError.Unauthorized>(result.apiErrorOrNull())
    }

    @Test
    fun safeApiCall_returns_Server_on_500() = runTest {
        val client = mockClient { _ ->
            respond("Internal Server Error", HttpStatusCode.InternalServerError)
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/1") }

        assertTrue(result.isFailure)
        val error = result.apiErrorOrNull()
        assertIs<ApiError.Server>(error)
        assertEquals(500, error.statusCode)
        assertTrue(error.isRetryable)
    }

    @Test
    fun safeApiCall_returns_RateLimited_on_429_with_retryAfter() = runTest {
        val client = mockClient { _ ->
            respond(
                """{"error":{"message":"Slow down"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.RetryAfter to listOf("60"),
                ),
            )
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users") }

        assertTrue(result.isFailure)
        val error = result.apiErrorOrNull()
        assertIs<ApiError.RateLimited>(error)
        assertEquals(60, error.retryAfterSeconds)
        assertEquals("Slow down", error.message)
    }

    @Test
    fun safeApiCall_returns_Network_on_exception() = runTest {
        val client = mockClient { _ ->
            throw RuntimeException("Connection refused")
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/1") }

        assertTrue(result.isFailure)
        val error = result.apiErrorOrNull()
        assertIs<ApiError.Network>(error)
        assertTrue(error.isRetryable)
    }

    @Test
    fun safeApiCallUnit_returns_success_on_204() = runTest {
        val client = mockClient { _ ->
            respond("", HttpStatusCode.NoContent)
        }

        val result: Result<Unit> = safeApiCallUnit { client.get("/users/1") }

        assertTrue(result.isSuccess)
    }

    @Test
    fun safeApiCall_handles_malformed_error_body_gracefully() = runTest {
        val client = mockClient { _ ->
            respond("not json at all", HttpStatusCode.BadRequest)
        }

        val result: Result<TestUser> = safeApiCall { client.get("/users/1") }

        assertTrue(result.isFailure)
        val error = result.apiErrorOrNull()
        assertIs<ApiError.BadRequest>(error)
    }
}
