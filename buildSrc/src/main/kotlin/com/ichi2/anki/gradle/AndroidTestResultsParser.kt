// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Parses JUnit-style XML test result files produced by AGP for connected Android tests.
 *
 * Root-level `<testsuites>` attributes (failures, skipped, time) are unreliable in AGP 9.x,
 * so we count individual `<testcase>` children instead.
 */
object AndroidTestResultsParser {
    data class AggregatedResults(
        val testCount: Long,
        val passed: Long,
        val failed: Long,
        val skipped: Long,
        val duration: Duration,
    ) {
        val isSuccessful: Boolean get() = failed == 0L && testCount > 0L
    }

    fun parseDirectory(directory: File): AggregatedResults? {
        if (!directory.exists() || !directory.isDirectory) {
            return null
        }

        val xmlFiles = directory.listFiles { _, name -> name.endsWith(".xml", ignoreCase = true) }
        if (xmlFiles.isNullOrEmpty()) {
            return null
        }

        var totalTests = 0L
        var totalFailures = 0L
        var totalErrors = 0L
        var totalSkipped = 0L
        var totalDurationMs = 0L

        for (file in xmlFiles) {
            val fileResult = parseFile(file) ?: continue
            totalTests += fileResult.testCount
            totalFailures += fileResult.failures
            totalErrors += fileResult.errors
            totalSkipped += fileResult.skipped
            totalDurationMs += fileResult.duration.inWholeMilliseconds
        }

        val failed = totalFailures + totalErrors
        val passed = (totalTests - failed - totalSkipped).coerceAtLeast(0L)

        return AggregatedResults(
            testCount = totalTests,
            passed = passed,
            failed = failed,
            skipped = totalSkipped,
            duration = totalDurationMs.milliseconds,
        )
    }

    data class FileTestResult(
        val testCount: Long,
        val failures: Long,
        val errors: Long,
        val skipped: Long,
        val duration: Duration,
    )

    fun parseFile(file: File): FileTestResult? {
        if (!file.isFile || file.length() == 0L) return null
        return file.inputStream().use { parseInputStream(it) }
    }

    fun parseInputStream(inputStream: InputStream): FileTestResult? {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        val root = doc.documentElement ?: return null

        return when (root.tagName.lowercase()) {
            "testsuite" -> countTestCases(root)
            "testsuites" -> aggregateFromTestsuites(root)
            else -> null
        }
    }

    private enum class TestCaseOutcome { PASSED, SKIPPED, ERROR, FAILURE }

    /**
     * Counts tests by inspecting each `<testcase>` child element.
     *
     * Tests are grouped by name to handle flaky-test retries: if a test fails then passes,
     * it counts as a single pass.
     *
     * AGP writes some skipped tests as `<failure>` tags rather than `<skipped/>`:
     * - `AssumptionViolatedException` – from `Assume.assumeTrue()` calls
     * - `"Test skipped"` – from `@Ignore`-annotated tests
     */
    private fun countTestCases(suite: Element): FileTestResult {
        val testCaseNodes = suite.getElementsByTagName("testcase")
        val testOutcomes = mutableMapOf<String, MutableList<TestCaseOutcome>>()

        for (i in 0 until testCaseNodes.length) {
            val tc = testCaseNodes.item(i) as? Element ?: continue
            val id = "${tc.getAttribute("classname")}#${tc.getAttribute("name")}"

            val outcome = when {
                tc.getElementsByTagName("skipped").length > 0 -> TestCaseOutcome.SKIPPED
                tc.getElementsByTagName("error").length > 0 -> TestCaseOutcome.ERROR
                tc.getElementsByTagName("failure").length > 0 -> {
                    val failureElem = tc.getElementsByTagName("failure").item(0) as? Element
                    val text = failureElem?.textContent?.trim() ?: ""
                    val msg = failureElem?.getAttribute("message") ?: ""
                    val type = failureElem?.getAttribute("type") ?: ""
                    if (text.contains("AssumptionViolatedException") ||
                        msg.contains("AssumptionViolatedException") ||
                        msg.equals("Test skipped", ignoreCase = true) ||
                        msg.equals("Test ignored", ignoreCase = true) ||
                        text.equals("Test skipped", ignoreCase = true) ||
                        text.equals("Test ignored", ignoreCase = true) ||
                        // @Ignore writes an empty <failure> with no text, message, or type
                        (text.isEmpty() && msg.isEmpty() && type.isEmpty())
                    ) {
                        TestCaseOutcome.SKIPPED
                    } else {
                        TestCaseOutcome.FAILURE
                    }
                }
                else -> TestCaseOutcome.PASSED
            }

            testOutcomes.getOrPut(id) { mutableListOf() }.add(outcome)
        }

        var totalTests = 0L
        var failures = 0L
        var errors = 0L
        var skipped = 0L

        if (testOutcomes.isNotEmpty()) {
            totalTests = testOutcomes.size.toLong()
            for ((_, outcomes) in testOutcomes) {
                when {
                    outcomes.contains(TestCaseOutcome.PASSED) -> { /* passed */ }
                    outcomes.contains(TestCaseOutcome.SKIPPED) -> skipped++
                    outcomes.contains(TestCaseOutcome.ERROR) -> errors++
                    else -> failures++
                }
            }
        } else {
            // No <testcase> children – fall back to suite-level attributes
            totalTests = suite.getAttribute("tests").toLongOrNull() ?: 0L
            skipped =
                suite.getAttribute("skipped").toLongOrNull()
                    ?: suite.getAttribute("ignored").toLongOrNull()
                    ?: 0L
            failures = suite.getAttribute("failures").toLongOrNull() ?: 0L
            errors = suite.getAttribute("errors").toLongOrNull() ?: 0L
        }

        val timeSeconds = suite.getAttribute("time").toDoubleOrNull() ?: 0.0
        val duration = (timeSeconds * 1000.0).roundToLong().milliseconds

        return FileTestResult(
            testCount = totalTests,
            failures = failures,
            errors = errors,
            skipped = skipped,
            duration = duration,
        )
    }

    private fun aggregateFromTestsuites(root: Element): FileTestResult {
        val suiteNodes = root.getElementsByTagName("testsuite")
        if (suiteNodes.length > 0) {
            var tests = 0L
            var failures = 0L
            var errors = 0L
            var skipped = 0L
            var totalDurationMs = 0L

            for (i in 0 until suiteNodes.length) {
                val suiteElem = suiteNodes.item(i) as? Element ?: continue
                val suiteResult = countTestCases(suiteElem)
                tests += suiteResult.testCount
                failures += suiteResult.failures
                errors += suiteResult.errors
                skipped += suiteResult.skipped
                totalDurationMs += suiteResult.duration.inWholeMilliseconds
            }

            // Prefer root time attribute (wall-clock total) over summed suite durations
            val rootTimeSeconds = root.getAttribute("time").toDoubleOrNull() ?: 0.0
            val rootDurationMs = (rootTimeSeconds * 1000.0).roundToLong()
            val finalDurationMs = if (rootDurationMs > 0L) rootDurationMs else totalDurationMs

            return FileTestResult(
                testCount = tests,
                failures = failures,
                errors = errors,
                skipped = skipped,
                duration = finalDurationMs.milliseconds,
            )
        }

        return countTestCases(root)
    }
}
