// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Reads Android connected test results XML files from [resultsDir] and appends
 * a summary row to [testSummaryService] (which writes Markdown to `$GITHUB_STEP_SUMMARY`).
 *
 * Designed to be Configuration Cache compatible.
 */
abstract class RecordAndroidTestSummaryTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val resultsDir: DirectoryProperty

    @get:Input
    abstract val suiteDisplayName: Property<String>

    @get:Internal
    abstract val testSummaryService: Property<TestSummaryService>

    @TaskAction
    fun recordSummary() {
        val service = testSummaryService.orNull
        if (service == null) {
            logger.info("Skipping emulator test step summary because testSummaryService is not configured.")
            return
        }

        val targetDir = resultsDir.orNull?.asFile ?: return
        val results = AndroidTestResultsParser.parseDirectory(targetDir) ?: return

        val resultStatus = if (results.isSuccessful) "SUCCESS" else "FAILURE"
        service.append(
            TestSummaryService.Row(
                suite = suiteDisplayName.get(),
                result = resultStatus,
                duration = results.duration,
                testCount = results.testCount,
                passed = results.passed,
                failed = results.failed,
                skipped = results.skipped,
            ),
        )

        logger.lifecycle(
            "Recorded test summary for {}: {} (Total: {}, Passed: {}, Failed: {}, Skipped: {}, Duration: {})",
            suiteDisplayName.get(),
            resultStatus,
            results.testCount,
            results.passed,
            results.failed,
            results.skipped,
            results.duration,
        )
    }
}
