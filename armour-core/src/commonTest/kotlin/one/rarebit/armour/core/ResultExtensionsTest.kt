package one.rarebit.armour.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResultExtensionsTest {

    @Test
    fun mapSuccess_transforms_success_value() {
        val result = Result.success(10).mapSuccess { it * 2 }
        assertEquals(20, result.getOrThrow())
    }

    @Test
    fun mapSuccess_preserves_failure() {
        val error = RuntimeException("boom")
        val result = Result.failure<Int>(error).mapSuccess { it * 2 }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun flatMap_chains_successful_results() {
        val result = Result.success(5).flatMap { Result.success(it + 3) }
        assertEquals(8, result.getOrThrow())
    }

    @Test
    fun flatMap_short_circuits_on_first_failure() {
        val error = RuntimeException("first")
        val result = Result.failure<Int>(error).flatMap { Result.success(it + 3) }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun flatMap_propagates_failure_from_transform() {
        val error = RuntimeException("second")
        val result = Result.success(5).flatMap<Int, Int> { Result.failure(error) }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun recover_transforms_failure_to_success() {
        val result = Result.failure<Int>(RuntimeException("boom")).recover { Result.success(42) }
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun recover_passes_through_success() {
        val result = Result.success(10).recover { Result.success(42) }
        assertEquals(10, result.getOrThrow())
    }

    @Test
    fun getOrElse_returns_success_value() {
        val value = Result.success(10).getOrElse { -1 }
        assertEquals(10, value)
    }

    @Test
    fun getOrElse_returns_default_on_failure() {
        val value = Result.failure<Int>(RuntimeException("boom")).getOrElse { -1 }
        assertEquals(-1, value)
    }

    @Test
    fun zip_combines_two_successes() {
        val result = Result.success("a").zip(Result.success(1))
        assertEquals("a" to 1, result.getOrThrow())
    }

    @Test
    fun zip_fails_with_first_error_when_left_fails() {
        val error = RuntimeException("left")
        val result = Result.failure<String>(error).zip(Result.success(1))
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun zip_fails_with_second_error_when_right_fails() {
        val error = RuntimeException("right")
        val result = Result.success("a").zip(Result.failure<Int>(error))
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun zip_three_combines_all_successes() {
        val result = Result.success("a").zip(Result.success(1), Result.success(true))
        assertEquals(Triple("a", 1, true), result.getOrThrow())
    }

    @Test
    fun zip_three_fails_on_any_failure() {
        val error = RuntimeException("middle")
        val result = Result.success("a").zip(Result.failure<Int>(error), Result.success(true))
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun apiErrorOrNull_returns_ApiError_from_failure() {
        val apiError = ApiError.NotFound()
        val result = Result.failure<String>(apiError)
        assertIs<ApiError.NotFound>(result.apiErrorOrNull())
    }

    @Test
    fun apiErrorOrNull_returns_null_for_non_ApiError() {
        val result = Result.failure<String>(RuntimeException("boom"))
        assertNull(result.apiErrorOrNull())
    }

    @Test
    fun apiErrorOrNull_returns_null_for_success() {
        val result = Result.success("ok")
        assertNull(result.apiErrorOrNull())
    }
}
