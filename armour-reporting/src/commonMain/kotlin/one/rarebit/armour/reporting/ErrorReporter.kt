package one.rarebit.armour.reporting

import one.rarebit.armour.core.ApiError

/**
 * Abstraction for reporting errors to external systems (Sentry, logging, analytics).
 *
 * Implement this interface to integrate with your preferred error tracking backend.
 * Use [CompositeReporter] to fan out to multiple reporters.
 */
interface ErrorReporter {
    /**
     * Report an error with optional context.
     *
     * @param error The error to report.
     * @param context Key-value pairs providing additional context (e.g., screen name, user action).
     */
    fun report(error: Throwable, context: Map<String, String> = emptyMap())

    /**
     * Record a breadcrumb for debugging context.
     *
     * Breadcrumbs are typically shown alongside the next reported error to provide
     * a trail of what happened before the failure.
     */
    fun breadcrumb(message: String, data: Map<String, String> = emptyMap())
}

/**
 * Fans out error reports to multiple [ErrorReporter] instances.
 *
 * ```kotlin
 * val reporter = CompositeReporter(sentryReporter, loggingReporter)
 * reporter.report(error) // sent to both Sentry and logs
 * ```
 */
class CompositeReporter(private val reporters: List<ErrorReporter>) : ErrorReporter {

    constructor(vararg reporters: ErrorReporter) : this(reporters.toList())

    override fun report(error: Throwable, context: Map<String, String>) {
        reporters.forEach { it.report(error, context) }
    }

    override fun breadcrumb(message: String, data: Map<String, String>) {
        reporters.forEach { it.breadcrumb(message, data) }
    }
}

/**
 * Simple reporter that delegates to a logging function. Useful for development
 * or as a fallback when no crash reporting SDK is configured.
 *
 * ```kotlin
 * val reporter = PrintReporter { tag, message -> Log.e(tag, message) }
 * ```
 */
class PrintReporter(
    private val tag: String = "Armour",
    private val log: (tag: String, message: String) -> Unit = { t, m -> println("[$t] $m") },
) : ErrorReporter {

    override fun report(error: Throwable, context: Map<String, String>) {
        val contextStr = if (context.isEmpty()) "" else " $context"
        val extra = if (error is ApiError) " [${error.statusCode}]" else ""
        log(tag, "ERROR$extra: ${error.message}$contextStr")
    }

    override fun breadcrumb(message: String, data: Map<String, String>) {
        val dataStr = if (data.isEmpty()) "" else " $data"
        log(tag, "BREADCRUMB: $message$dataStr")
    }
}

/** An [ErrorReporter] that silently discards all reports. Useful for testing. */
object NoOpReporter : ErrorReporter {
    override fun report(error: Throwable, context: Map<String, String>) {}
    override fun breadcrumb(message: String, data: Map<String, String>) {}
}
