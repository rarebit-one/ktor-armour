package one.rarebit.armour.core

/**
 * Transform the success value while preserving failures.
 */
inline fun <T, R> Result<T>.mapSuccess(transform: (T) -> R): Result<R> =
    fold(
        onSuccess = { Result.success(transform(it)) },
        onFailure = { Result.failure(it) },
    )

/**
 * Flat-map: transform the success value into another Result.
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(
        onSuccess = { transform(it) },
        onFailure = { Result.failure(it) },
    )

/**
 * Recover from a failure by producing a new Result.
 */
inline fun <T> Result<T>.recover(transform: (Throwable) -> Result<T>): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { transform(it) },
    )

/**
 * Return the success value, or compute a default from the error.
 */
inline fun <T> Result<T>.getOrElse(default: (Throwable) -> T): T =
    fold(
        onSuccess = { it },
        onFailure = { default(it) },
    )

/**
 * Combine two Results into a Pair. Fails with the first error encountered.
 */
fun <A, B> Result<A>.zip(other: Result<B>): Result<Pair<A, B>> =
    flatMap { a -> other.mapSuccess { b -> a to b } }

/**
 * Combine three Results. Fails with the first error encountered.
 */
fun <A, B, C> Result<A>.zip(
    second: Result<B>,
    third: Result<C>,
): Result<Triple<A, B, C>> =
    flatMap { a ->
        second.flatMap { b ->
            third.mapSuccess { c -> Triple(a, b, c) }
        }
    }

/**
 * Extract the [ApiError] from a failed Result, or null if successful or a different exception.
 */
fun <T> Result<T>.apiErrorOrNull(): ApiError? =
    exceptionOrNull() as? ApiError
