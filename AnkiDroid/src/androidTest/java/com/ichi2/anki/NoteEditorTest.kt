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
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.Matchers
import org.junit.Assume
import org.junit.Before
import org.junit.Rule

@KotlinCleanup("fix ide lint issues")
abstract class NoteEditorTest protected constructor() {
    private val isInvalid = invalidSdksImpl.contains(Build.VERSION.SDK_INT)
    // Rules mean that we get a failure on API 25.
    // Even if we ignore the tests, the rules cause a failure.
    // We can't ignore the test in @BeforeClass ("Test run failed to complete. Expected 150 tests, received 149")
    // and @Before executes after the rule.
    // So, disable the rules in the constructor, and ignore in before.
    @get:Rule
    var runtimePermissionRule =
        if (isInvalid)
            null
        else
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @get:Rule
    var activityRule: ActivityScenarioRule<NoteEditor>? =
        if (isInvalid)
            null
        else
            ActivityScenarioRule(noteEditorIntent)

    @KotlinCleanup("simplify property getter with apply and direct return")
    private val noteEditorIntent: Intent
        get() {
            val intent = Intent(targetContext, NoteEditor::class.java)
            intent.component = ComponentName(targetContext, NoteEditor::class.java)
            intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
            return intent
        }

    @Before
    fun before() {
        for (invalid in invalidSdksImpl) {
            Assume.assumeThat(
                String.format("Test fails on Travis API %d", invalid),
                Build.VERSION.SDK_INT,
                Matchers.not(
                    Matchers.`is`(invalid)
                )
            )
        }
    }

    protected val invalidSdksImpl: List<Int>
        get() {
            // TODO: Look into these assumptions and see if they can be diagnosed - both work on my emulators.
            // If we fix them, we might be able to use instrumentation.sendKeyDownUpSync
            /*
             java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
             at androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:301)
              */
            return mutableListOf(Build.VERSION_CODES.N_MR1).apply { addAll(invalidSdks) }
        }
    protected open val invalidSdks = listOf<Int>()
    protected val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
}
