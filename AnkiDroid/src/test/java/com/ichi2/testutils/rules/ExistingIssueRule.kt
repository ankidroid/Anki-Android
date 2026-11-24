// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import com.ichi2.anki.BuildConfig
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Marks a regression test for a defect that already exists in the base branch. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ExistingIssue(
    val value: Int = 0,
    val repository: String = "ankidroid/Anki-Android",
)

/** Skip known defects locally and require their issue IDs in CI. */
class ExistingIssueRule(
    private val inCi: Boolean = BuildConfig.CI,
    private val runLocally: Boolean = System.getProperty("ankidroid.runExistingIssueTests") == "true",
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        val issue = description.getAnnotation(ExistingIssue::class.java) ?: return base
        return object : Statement() {
            override fun evaluate() {
                if (inCi && issue.value <= 0) {
                    throw AssertionError("Missing GitHub issue ID for @ExistingIssue on ${description.displayName}")
                }
                if (inCi || !runLocally) {
                    val reason =
                        if (issue.value > 0) {
                            "Existing issue at https://github.com/${issue.repository}/issues/${issue.value}"
                        } else {
                            "Existing issue awaiting a GitHub issue ID"
                        }
                    throw AssumptionViolatedException(reason)
                }
                base.evaluate()
            }
        }
    }
}
