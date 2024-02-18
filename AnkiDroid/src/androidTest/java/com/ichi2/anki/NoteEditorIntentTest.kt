/*
 *  Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditor.Companion.intentLaunchedWithImage
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import junit.framework.TestCase.assertFalse
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteEditorIntentTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule: TestRule? = GrantStoragePermission.instance

    @get:Rule
    var activityRuleIntent: ActivityScenarioRule<NoteEditor>? = ActivityScenarioRule(
        noteEditorTextIntent
    )

    @Test
    fun launchActivityWithIntent() {
        col
        val scenario = activityRuleIntent!!.scenario
        scenario.moveToState(Lifecycle.State.RESUMED)

        onActivity(scenario) { editor ->
            val currentFieldStrings = editor.currentFieldStrings
            MatcherAssert.assertThat(currentFieldStrings[0], Matchers.equalTo("sample text"))
        }
    }

    @Test
    fun intentLaunchedWithNonImageIntent() {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
        }
        assertFalse(intentLaunchedWithImage(intent))
    }

    private val noteEditorTextIntent: Intent
        get() {
            return Intent(testContext, NoteEditor::class.java).apply {
                component = ComponentName(testContext, NoteEditor::class.java)
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "sample text")
            }
        }

    @Throws(Throwable::class)
    private fun onActivity(
        scenario: ActivityScenario<NoteEditor>,
        noteEditorActivityAction: ActivityScenario.ActivityAction<NoteEditor>
    ) {
        val wrapped = AtomicReference<Throwable?>(null)
        scenario.onActivity { a: NoteEditor ->
            try {
                noteEditorActivityAction.perform(a)
            } catch (t: Throwable) {
                wrapped.set(t)
            }
        }
        wrapped.get()?.let { throw it }
    }
}
