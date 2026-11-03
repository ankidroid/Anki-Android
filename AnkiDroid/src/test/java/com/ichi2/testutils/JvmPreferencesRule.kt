// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.SharedPreferences
import android.content.res.Resources
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import io.mockk.every
import io.mockk.mockk
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Isolate singleton preferences without constructing an Android application or loading resources. */
class JvmPreferencesRule : TestRule {
    val preferences: SharedPreferences = SPMockBuilder().createSharedPreferences()
    private val resources =
        mockk<Resources>().also { resources ->
            every { resources.getString(any()) } answers { firstArg<Int>().toString() }
        }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = AnkiDroidApp.sharedPreferencesTestingOverride
                val previousResources = AnkiDroidApp.resourcesTestingOverride
                try {
                    AnkiDroidApp.resourcesTestingOverride = resources
                    AnkiDroidApp.sharedPreferencesTestingOverride = preferences
                    base.evaluate()
                } finally {
                    AnkiDroidApp.sharedPreferencesTestingOverride = previous
                    AnkiDroidApp.resourcesTestingOverride = previousResources
                }
            }
        }
}
