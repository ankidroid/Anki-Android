// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class AndroidTestResultsParserTest {

    @Test
    fun parseSingleTestSuiteSuccess(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.ichi2.anki.DeckPickerTest" tests="2" failures="0" errors="0" skipped="0" time="12.5">
              <testcase name="test1" classname="com.ichi2.anki.DeckPickerTest" time="5.0" />
              <testcase name="test2" classname="com.ichi2.anki.DeckPickerTest" time="7.5" />
            </testsuite>
            """.trimIndent()

        val file = File(tempDir, "TEST-testsuite.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(2L, result!!.testCount)
        assertEquals(2L, result.passed)
        assertEquals(0L, result.failed)
        assertEquals(0L, result.skipped)
        assertEquals(12500.milliseconds, result.duration)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun parseSingleTestSuiteWithFailuresAndSkipped(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.ichi2.anki.DeckPickerTest" tests="5" failures="1" errors="1" skipped="1" time="10.0">
              <testcase name="pass1" classname="com.ichi2.anki.DeckPickerTest" time="1.0" />
              <testcase name="pass2" classname="com.ichi2.anki.DeckPickerTest" time="1.0" />
              <testcase name="fail1" classname="com.ichi2.anki.DeckPickerTest" time="1.0">
                <failure message="assertion failed">assertion failed</failure>
              </testcase>
              <testcase name="err1" classname="com.ichi2.anki.DeckPickerTest" time="1.0">
                <error message="NullPointerException">NullPointerException</error>
              </testcase>
              <testcase name="skip1" classname="com.ichi2.anki.DeckPickerTest" time="1.0">
                <skipped/>
              </testcase>
            </testsuite>
            """.trimIndent()

        val file = File(tempDir, "TEST-failures.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(5L, result!!.testCount)
        assertEquals(2L, result.passed)
        assertEquals(2L, result.failed) // 1 failure + 1 error
        assertEquals(1L, result.skipped)
        assertEquals(10000.milliseconds, result.duration)
        assertFalse(result.isSuccessful)
    }

    @Test
    fun parseTestSuitesRootAggregatesChildTestcases(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuites tests="4" failures="0" errors="0" skipped="0" time="25.0">
              <testsuite name="SuiteA" tests="2" failures="0" errors="0" skipped="0" time="12.0">
                <testcase name="a1" classname="SuiteA" time="6.0" />
                <testcase name="a2" classname="SuiteA" time="6.0" />
              </testsuite>
              <testsuite name="SuiteB" tests="2" failures="0" errors="0" skipped="0" time="13.0">
                <testcase name="b1" classname="SuiteB" time="6.5" />
                <testcase name="b2" classname="SuiteB" time="6.5" />
              </testsuite>
            </testsuites>
            """.trimIndent()

        val file = File(tempDir, "TEST-testsuites-aggregated.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(4L, result!!.testCount)
        assertEquals(4L, result.passed)
        assertEquals(0L, result.failed)
        assertEquals(0L, result.skipped)
        assertEquals(25000.milliseconds, result.duration)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun parseTestSuitesRootWithoutChildTestcases(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuites>
              <testsuite name="SuiteA" tests="3" failures="1" errors="0" skipped="0" time="5.0" />
              <testsuite name="SuiteB" tests="4" failures="0" errors="1" skipped="1" time="7.0" />
            </testsuites>
            """.trimIndent()

        val file = File(tempDir, "TEST-testsuites-nested.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(7L, result!!.testCount)
        assertEquals(4L, result.passed)
        assertEquals(2L, result.failed)
        assertEquals(1L, result.skipped)
        assertEquals(12000.milliseconds, result.duration)
        assertFalse(result.isSuccessful)
    }

    @Test
    fun parseAgpConnectedAndroidTestSuitesWithAssumptionFailures(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuites tests="348" failures="9" errors="0" skipped="0">
              <testsuite name="com.ichi2.anki.Suite1" tests="300" failures="0" errors="0" skipped="0" time="120.5">
                <testcase name="test1" classname="com.ichi2.anki.Suite1" time="0.4" />
                <testcase name="test2" classname="com.ichi2.anki.Suite1" time="0.4" />
              </testsuite>
              <testsuite name="com.ichi2.anki.Suite2" tests="48" failures="9" errors="0" skipped="0" time="45.5">
                <testcase name="assumptionFail1" classname="com.ichi2.anki.Suite2" time="0.1">
                  <failure>org.junit.AssumptionViolatedException: got: &lt;false&gt;, expected: is &lt;true&gt;</failure>
                </testcase>
                <testcase name="assumptionFail2" classname="com.ichi2.anki.Suite2" time="0.1">
                  <failure>org.junit.AssumptionViolatedException: got: &lt;false&gt;, expected: is &lt;true&gt;</failure>
                </testcase>
                <testcase name="normalPass" classname="com.ichi2.anki.Suite2" time="0.4" />
              </testsuite>
            </testsuites>
            """.trimIndent()

        val file = File(tempDir, "TEST-emulator-play-release.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(5L, result!!.testCount)
        assertEquals(3L, result.passed)
        assertEquals(0L, result.failed)
        assertEquals(2L, result.skipped)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun parseFlakyTestRetries(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuites>
              <testsuite name="com.ichi2.anki.FlakySuite" tests="2" failures="1" time="10.0">
                <testcase name="flakyTest" classname="com.ichi2.anki.FlakySuite" time="1.0">
                  <failure message="assertion failed">assertion failed</failure>
                </testcase>
                <testcase name="flakyTest" classname="com.ichi2.anki.FlakySuite" time="1.0" />
                <testcase name="stableTest" classname="com.ichi2.anki.FlakySuite" time="1.0" />
              </testsuite>
            </testsuites>
            """.trimIndent()

        val file = File(tempDir, "TEST-flaky.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(2L, result!!.testCount)
        assertEquals(2L, result.passed)
        assertEquals(0L, result.failed)
        assertEquals(0L, result.skipped)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun parseIgnoreAnnotatedTestsAsSkipped(
        @TempDir tempDir: File,
    ) {
        val xmlContent =
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuites>
              <testsuite name="com.ichi2.anki.NoteEditorTabOrderTest" tests="2" failures="1" time="5.0">
                <testcase name="testTabOrder" classname="com.ichi2.anki.NoteEditorTabOrderTest" time="0.0">
                  <failure/>
                </testcase>
                <testcase name="anotherTest" classname="com.ichi2.anki.NoteEditorTabOrderTest" time="1.0" />
              </testsuite>
            </testsuites>
            """.trimIndent()

        val file = File(tempDir, "TEST-ignore.xml")
        file.writeText(xmlContent)

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(2L, result!!.testCount)
        assertEquals(1L, result.passed)
        assertEquals(0L, result.failed)
        assertEquals(1L, result.skipped)
        assertTrue(result.isSuccessful)
    }

    @Test
    fun parseDirectoryWithMultipleFiles(
        @TempDir tempDir: File,
    ) {
        val file1 = File(tempDir, "TEST-device1.xml")
        file1.writeText(
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="Suite1" tests="3" failures="0" errors="0" skipped="0" time="4.0">
              <testcase name="t1" classname="Suite1" time="1.0" />
              <testcase name="t2" classname="Suite1" time="1.5" />
              <testcase name="t3" classname="Suite1" time="1.5" />
            </testsuite>
            """.trimIndent(),
        )

        val file2 = File(tempDir, "TEST-device2.xml")
        file2.writeText(
            """
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="Suite2" tests="2" failures="1" errors="0" skipped="1" time="6.0">
              <testcase name="t4" classname="Suite2" time="2.0">
                <failure message="AssertionError">AssertionError</failure>
              </testcase>
              <testcase name="t5" classname="Suite2" time="4.0">
                <skipped/>
              </testcase>
            </testsuite>
            """.trimIndent(),
        )

        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNotNull(result)
        assertEquals(5L, result!!.testCount)
        assertEquals(3L, result.passed)
        assertEquals(1L, result.failed)
        assertEquals(1L, result.skipped)
        assertEquals(10000.milliseconds, result.duration)
        assertFalse(result.isSuccessful)
    }

    @Test
    fun parseNonExistentDirectory() {
        val nonExistent = File("non/existent/path/for/test")
        val result = AndroidTestResultsParser.parseDirectory(nonExistent)
        assertNull(result)
    }

    @Test
    fun parseEmptyDirectory(
        @TempDir tempDir: File,
    ) {
        val result = AndroidTestResultsParser.parseDirectory(tempDir)
        assertNull(result)
    }
}
