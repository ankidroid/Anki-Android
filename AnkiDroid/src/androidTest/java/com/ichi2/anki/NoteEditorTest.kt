/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.hamcrest.Matchers
import org.junit.Assume
import org.junit.Before
import org.junit.Rule

abstract class NoteEditorTest protected constructor() {
    @get:Rule
    var runtimePermissionRule: GrantPermissionRule? =
        GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @get:Rule
    var activityRule: ActivityScenarioRule<NoteEditor>? = ActivityScenarioRule(
        noteEditorIntent
    )

    private val noteEditorIntent: Intent
        get() {
            return Intent(targetContext, NoteEditor::class.java).apply {
                component = ComponentName(targetContext, NoteEditor::class.java)
                putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
            }
        }

    @Before
    fun before() {
        for (invalid in invalidSdksImpl) {
            Assume.assumeThat(
                "Test fails on Travis API $invalid",
                Build.VERSION.SDK_INT,
                Matchers.not(
                    Matchers.`is`(invalid)
                )
            )
        }
    }
    protected open val invalidSdks: List<Int>? = emptyList()
    private val invalidSdksImpl: List<Int> = listOf(Build.VERSION_CODES.N_MR1) + invalidSdks.orEmpty()
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        // Rules mean that we get a failure on API 25.
        // Even if we ignore the tests, the rules cause a failure.
        // We can't ignore the test in @BeforeClass ("Test run failed to complete. Expected 150 tests, received 149")
        // and @Before executes after the rule.
        // So, disable the rules in the constructor, and ignore in before.
        if (invalidSdksImpl.contains(Build.VERSION.SDK_INT)) {
            activityRule = null
            runtimePermissionRule = null
        }
    }
}
