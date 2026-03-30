package one.rarebit.armour.retry

import kotlinx.coroutines.test.runTest
import one.rarebit.armour.core.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetryPolicyTest {

    private val policy = RetryPolicy(
        maxRetries = 3,
        initialDelayMs = 1,
        maxDelayMs = 10,
        backoffMultiplier = 2.0,
        jitter = false,
    )

    @Test
    fun succeeds_on_first_attempt() = runTest {
        val result = policy.execute { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun retries_on_server_error_then_succeeds() = runTest {
        var attempts = 0
        val result = policy.execute {
            attempts++
            if (attempts < 3) throw ApiError.Server(500)
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, attempts)
    }

    @Test
    fun retries_on_network_error_then_succeeds() = runTest {
        var attempts = 0
        val result = policy.execute {
            attempts++
            if (attempts < 2) throw ApiError.Network(cause = RuntimeException("timeout"))
            "connected"
        }
        assertEquals("connected", result)
        assertEquals(2, attempts)
    }

    @Test
    fun does_not_retry_client_errors() = runTest {
        var attempts = 0
        assertFailsWith<ApiError.NotFound> {
            policy.execute {
                attempts++
                throw ApiError.NotFound()
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun does_not_retry_unauthorized() = runTest {
        var attempts = 0
        assertFailsWith<ApiError.Unauthorized> {
            policy.execute {
                attempts++
                throw ApiError.Unauthorized()
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun does_not_retry_validation_errors() = runTest {
        var attempts = 0
        assertFailsWith<ApiError.ValidationError> {
            policy.execute {
                attempts++
                throw ApiError.ValidationError()
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun exhausts_retries_then_throws() = runTest {
        var attempts = 0
        assertFailsWith<ApiError.Server> {
            policy.execute {
                attempts++
                throw ApiError.Server(503)
            }
        }
        assertEquals(4, attempts) // 1 initial + 3 retries
    }

    @Test
    fun retries_rate_limited_errors() = runTest {
        val fastPolicy = RetryPolicy(maxRetries = 1, initialDelayMs = 1, maxDelayMs = 10, jitter = false)
        var attempts = 0
        val result = fastPolicy.execute {
            attempts++
            if (attempts < 2) throw ApiError.RateLimited()
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun retries_generic_exceptions() = runTest {
        var attempts = 0
        val result = policy.execute {
            attempts++
            if (attempts < 2) throw RuntimeException("transient")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun passes_attempt_number_to_block() = runTest {
        val attemptNumbers = mutableListOf<Int>()
        val result = policy.execute { attempt ->
            attemptNumbers.add(attempt)
            if (attempt < 2) throw ApiError.Server(500)
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(listOf(0, 1, 2), attemptNumbers)
    }

    @Test
    fun zero_retries_means_single_attempt() = runTest {
        val noRetryPolicy = RetryPolicy(maxRetries = 0, initialDelayMs = 1, maxDelayMs = 10, jitter = false)
        var attempts = 0
        assertFailsWith<ApiError.Server> {
            noRetryPolicy.execute {
                attempts++
                throw ApiError.Server(500)
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun constructor_validates_parameters() {
        assertFailsWith<IllegalArgumentException> { RetryPolicy(maxRetries = -1) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(initialDelayMs = 0) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(initialDelayMs = 100, maxDelayMs = 50) }
        assertFailsWith<IllegalArgumentException> { RetryPolicy(backoffMultiplier = 0.5) }
    }
}
