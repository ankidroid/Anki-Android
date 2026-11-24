// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.rules

import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Result
import org.junit.runner.manipulation.Filter
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.fail

class ExistingIssueRuleTest {
    @Test
    fun `local runs skip missing and linked regressions before setup`() {
        val rule = ExistingIssueRule(inCi = false, runLocally = false)
        for (issue in listOf(null, ExistingIssue(123, "ankidroid/Anki-Android-Backend"))) {
            assertSkipped(runFixture(rule, SetupFixture::class.java, issue))
        }
    }

    @Test
    fun `CI requires a positive issue ID even if local reproduction is enabled`() {
        val rule = ExistingIssueRule(inCi = true, runLocally = true)
        for (issue in listOf(null, ExistingIssue(-1))) {
            val result = runFixture(rule, SetupFixture::class.java, issue)
            assertContains(result.failures.single().message!!, "Missing GitHub issue ID for @ExistingIssue")
        }
        assertSkipped(runFixture(rule, SetupFixture::class.java, ExistingIssue(123)))
    }

    @Test
    fun `local opt-in executes the regression assertions`() {
        val rule = ExistingIssueRule(inCi = false, runLocally = true)
        for (issue in listOf(null, ExistingIssue(123))) {
            val result = runFixture(rule, syntheticIssue = issue)
            assertEquals("regression assertion", result.failures.single().message)
        }
    }

    @Test
    fun `ordinary tests retain their successes and failures`() {
        val rule = ExistingIssueRule(inCi = true, runLocally = false)
        val success = runFixture(rule, methodName = "ordinarySuccess")
        assertEquals(1, success.runCount)
        assertEquals(0, success.assumptionFailureCount)
        assertEquals(0, success.failureCount)
        val failure = runFixture(rule, methodName = "ordinaryFailure")
        assertEquals("ordinary assertion", failure.failures.single().message)
    }

    @Test
    fun `default rule uses the CI and local opt-in settings of the test worker`() {
        val result = runFixture(ExistingIssueRule())
        when {
            System.getenv("CI") == "true" -> assertContains(result.failures.single().message!!, "Missing GitHub issue ID")
            System.getProperty(
                "ankidroid.runExistingIssueTests",
            ) == "true" -> assertEquals("regression assertion", result.failures.single().message)
            else -> assertSkipped(result)
        }
    }

    private fun assertSkipped(result: Result) {
        assertEquals(1, result.assumptionFailureCount)
        assertEquals(0, result.failureCount)
    }

    private fun runFixture(
        rule: ExistingIssueRule,
        fixture: Class<*> = RegressionFixture::class.java,
        syntheticIssue: ExistingIssue? = null,
        methodName: String = "regression",
    ): Result {
        val runner =
            object : BlockJUnit4ClassRunner(fixture) {
                override fun getTestRules(target: Any): List<TestRule> = listOf(rule)

                override fun describeChild(method: FrameworkMethod): Description =
                    if (syntheticIssue == null) {
                        super.describeChild(method)
                    } else {
                        Description.createTestDescription(fixture, method.name, syntheticIssue)
                    }
            }
        runner.filter(Filter.matchMethodDescription(Description.createTestDescription(fixture, methodName)))
        return JUnitCore().run(runner)
    }

    @Ignore("Run explicitly by ExistingIssueRuleTest")
    class SetupFixture {
        @Before
        fun setUp() {
            fail("gate must run before setup")
        }

        @Test
        @ExistingIssue
        fun regression() {
            fail("gate must run before the regression")
        }
    }

    @Ignore("Run explicitly by ExistingIssueRuleTest")
    class RegressionFixture {
        @Test
        @ExistingIssue
        fun regression() {
            fail("regression assertion")
        }

        @Test
        fun ordinarySuccess() {}

        @Test
        fun ordinaryFailure() {
            fail("ordinary assertion")
        }
    }
}
