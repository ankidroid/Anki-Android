/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extracts the values [TestSummaryService] needs from each root test-suite
 * completion and forwards them. Holding only a [Provider] reference keeps
 * the listener configuration-cache safe.
 */
class GitHubActionsTestListener(
    private val service: Provider<TestSummaryService>,
) : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}

    override fun afterTest(
        testDescriptor: TestDescriptor,
        result: TestResult,
    ) {}

    override fun beforeTest(testDescriptor: TestDescriptor) {}

    override fun afterSuite(
        suite: TestDescriptor,
        result: TestResult,
    ) {
        if (suite.parent != null) return // only log for the root suite
        service.get().append(
            TestSummaryService.Row(
                suite = suite.displayName,
                result = result.resultType.name,
                duration = (result.endTime - result.startTime).milliseconds,
                testCount = result.testCount,
                passed = result.successfulTestCount,
                failed = result.failedTestCount,
                skipped = result.skippedTestCount,
            ),
        )
    }
}
