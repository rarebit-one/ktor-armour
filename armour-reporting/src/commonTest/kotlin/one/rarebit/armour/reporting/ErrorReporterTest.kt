package one.rarebit.armour.reporting

import one.rarebit.armour.core.ApiError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorReporterTest {

    @Test
    fun compositeReporter_fans_out_to_all_reporters() {
        val log1 = mutableListOf<String>()
        val log2 = mutableListOf<String>()

        val reporter1 = PrintReporter("R1") { _, msg -> log1.add(msg) }
        val reporter2 = PrintReporter("R2") { _, msg -> log2.add(msg) }
        val composite = CompositeReporter(reporter1, reporter2)

        composite.report(RuntimeException("boom"))

        assertEquals(1, log1.size)
        assertEquals(1, log2.size)
        assertTrue(log1.first().contains("boom"))
        assertTrue(log2.first().contains("boom"))
    }

    @Test
    fun compositeReporter_fans_out_breadcrumbs() {
        val log1 = mutableListOf<String>()
        val log2 = mutableListOf<String>()

        val reporter1 = PrintReporter("R1") { _, msg -> log1.add(msg) }
        val reporter2 = PrintReporter("R2") { _, msg -> log2.add(msg) }
        val composite = CompositeReporter(reporter1, reporter2)

        composite.breadcrumb("user tapped button")

        assertEquals(1, log1.size)
        assertEquals(1, log2.size)
        assertTrue(log1.first().contains("user tapped button"))
    }

    @Test
    fun printReporter_includes_status_code_for_ApiError() {
        val messages = mutableListOf<String>()
        val reporter = PrintReporter("Test") { _, msg -> messages.add(msg) }

        reporter.report(ApiError.NotFound(message = "User not found"))

        assertEquals(1, messages.size)
        assertTrue(messages.first().contains("[404]"))
        assertTrue(messages.first().contains("User not found"))
    }

    @Test
    fun printReporter_includes_context() {
        val messages = mutableListOf<String>()
        val reporter = PrintReporter("Test") { _, msg -> messages.add(msg) }

        reporter.report(
            RuntimeException("oops"),
            context = mapOf("screen" to "home", "action" to "refresh"),
        )

        assertTrue(messages.first().contains("screen"))
        assertTrue(messages.first().contains("home"))
    }

    @Test
    fun printReporter_breadcrumb_includes_data() {
        val messages = mutableListOf<String>()
        val reporter = PrintReporter("Test") { _, msg -> messages.add(msg) }

        reporter.breadcrumb("navigated", mapOf("to" to "settings"))

        assertTrue(messages.first().contains("BREADCRUMB"))
        assertTrue(messages.first().contains("navigated"))
        assertTrue(messages.first().contains("settings"))
    }

    @Test
    fun noOpReporter_does_nothing() {
        // Just verifying it doesn't throw
        NoOpReporter.report(RuntimeException("boom"))
        NoOpReporter.breadcrumb("test")
    }

    @Test
    fun compositeReporter_with_empty_list() {
        val composite = CompositeReporter(emptyList())
        // Should not throw
        composite.report(RuntimeException("boom"))
        composite.breadcrumb("test")
    }
}
