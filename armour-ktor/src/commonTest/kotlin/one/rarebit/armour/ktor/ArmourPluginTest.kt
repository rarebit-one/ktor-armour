package one.rarebit.armour.ktor

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import one.rarebit.armour.core.ApiError
import one.rarebit.armour.reporting.ErrorReporter
import one.rarebit.armour.retry.RetryPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ArmourPluginTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun passes_through_successful_responses() = runTest {
        val client = createClient { _ ->
            respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
        }

        val response = client.get("/test")
        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().contains("ok"))
    }

    @Test
    fun converts_404_to_NotFound() = runTest {
        val client = createClient(retryPolicy = null) { _ ->
            respond(
                """{"error":{"code":"not_found","message":"Gone"}}""",
                HttpStatusCode.NotFound,
                jsonHeaders,
            )
        }

        val error = assertFailsWith<ApiError.NotFound> { client.get("/missing") }
        assertEquals("not_found", error.code)
        assertEquals("Gone", error.message)
    }

    @Test
    fun converts_401_to_Unauthorized() = runTest {
        val client = createClient(retryPolicy = null) { _ ->
            respond("", HttpStatusCode.Unauthorized)
        }

        assertFailsWith<ApiError.Unauthorized> { client.get("/secure") }
    }

    @Test
    fun converts_500_to_Server() = runTest {
        val client = createClient(retryPolicy = null) { _ ->
            respond("boom", HttpStatusCode.InternalServerError)
        }

        val error = assertFailsWith<ApiError.Server> { client.get("/broken") }
        assertEquals(500, error.statusCode)
    }

    @Test
    fun retries_server_errors() = runTest {
        var attempts = 0
        val client = createClient(
            retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1, maxDelayMs = 10, jitter = false),
        ) { _ ->
            attempts++
            if (attempts < 3) {
                respond("error", HttpStatusCode.InternalServerError)
            } else {
                respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val response = client.get("/flaky")
        assertEquals(200, response.status.value)
        assertEquals(3, attempts)
    }

    @Test
    fun does_not_retry_client_errors() = runTest {
        var attempts = 0
        val client = createClient(
            retryPolicy = RetryPolicy(maxRetries = 3, initialDelayMs = 1, maxDelayMs = 10, jitter = false),
        ) { _ ->
            attempts++
            respond("", HttpStatusCode.BadRequest)
        }

        assertFailsWith<ApiError.BadRequest> { client.get("/bad") }
        assertEquals(1, attempts)
    }

    @Test
    fun reports_errors_to_reporter() = runTest {
        val reported = mutableListOf<Throwable>()
        val reporter = object : ErrorReporter {
            override fun report(error: Throwable, context: Map<String, String>) {
                reported.add(error)
            }
            override fun breadcrumb(message: String, data: Map<String, String>) {}
        }

        val client = createClient(retryPolicy = null, reporter = reporter) { _ ->
            respond("", HttpStatusCode.Forbidden)
        }

        assertFailsWith<ApiError.Forbidden> { client.get("/nope") }
        assertEquals(1, reported.size)
        assertIs<ApiError.Forbidden>(reported.first())
    }

    @Test
    fun suppressed_status_codes_are_not_reported() = runTest {
        val reported = mutableListOf<Throwable>()
        val reporter = object : ErrorReporter {
            override fun report(error: Throwable, context: Map<String, String>) {
                reported.add(error)
            }
            override fun breadcrumb(message: String, data: Map<String, String>) {}
        }

        val client = createClient(
            retryPolicy = null,
            reporter = reporter,
            suppressedStatusCodes = setOf(401),
        ) { _ ->
            respond("", HttpStatusCode.Unauthorized)
        }

        assertFailsWith<ApiError.Unauthorized> { client.get("/login") }
        assertTrue(reported.isEmpty(), "401 should not be reported when suppressed")
    }

    @Test
    fun records_breadcrumbs_on_retry() = runTest {
        val breadcrumbs = mutableListOf<String>()
        val reporter = object : ErrorReporter {
            override fun report(error: Throwable, context: Map<String, String>) {}
            override fun breadcrumb(message: String, data: Map<String, String>) {
                breadcrumbs.add(message)
            }
        }

        var attempts = 0
        val client = createClient(
            retryPolicy = RetryPolicy(maxRetries = 2, initialDelayMs = 1, maxDelayMs = 10, jitter = false),
            reporter = reporter,
        ) { _ ->
            attempts++
            if (attempts < 3) {
                respond("error", HttpStatusCode.InternalServerError)
            } else {
                respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        client.get("/flaky")
        assertEquals(2, breadcrumbs.size)
        assertTrue(breadcrumbs.all { it.contains("Retrying") })
    }

    private fun createClient(
        retryPolicy: RetryPolicy? = null,
        reporter: ErrorReporter? = null,
        suppressedStatusCodes: Set<Int> = emptySet(),
        handler: io.ktor.client.engine.mock.MockRequestHandler,
    ): HttpClient = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json() }
        expectSuccess = false
        install(Armour) {
            this.retryPolicy = retryPolicy
            if (reporter != null) this.reporter = reporter
            this.suppressedStatusCodes = suppressedStatusCodes
        }
    }
}
