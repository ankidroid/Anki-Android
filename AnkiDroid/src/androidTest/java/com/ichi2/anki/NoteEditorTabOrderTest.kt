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

import android.app.Activity
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteEditorTabOrderTest : NoteEditorTest() {
    override val invalidSdks: List<Int>
        /*
            java.lang.AssertionError:
            Expected: is "a"
         */
        get() = listOf(30)

    @Test
    @Ignore(
        """flaky on API 21 as well: com.ichi2.anki.NoteEditorTabOrderTest > testTabOrder[test(AVD) - 5.1.1] FAILED 

	java.lang.AssertionError:

	Expected: is "a""""
    )
    @Throws(Throwable::class)
    fun testTabOrder() {
        ensureCollectionLoaded()
        val scenario = activityRule!!.scenario
        scenario.moveToState(Lifecycle.State.RESUMED)

        onActivity(scenario) { editor: NoteEditor ->
            sendKeyDownUp(editor, KeyEvent.KEYCODE_A)
            sendKeyDownUp(editor, KeyEvent.KEYCODE_TAB)
            sendKeyDownUp(editor, KeyEvent.KEYCODE_TAB)
            sendKeyDownUp(editor, KeyEvent.KEYCODE_B)
        }

        onActivity(scenario) { editor: NoteEditor ->
            val currentFieldStrings = editor.currentFieldStrings
            assertThat(currentFieldStrings[0], equalTo("a"))
            assertThat(currentFieldStrings[1], equalTo("b"))
        }
    }

    private fun sendKeyDownUp(activity: Activity, keyCode: Int) {
        val inputConnection = BaseInputConnection(activity.currentFocus, true)
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Throws(Throwable::class)
    private fun onActivity(
        scenario: ActivityScenario<NoteEditor>,
        noteEditorActivityAction: ActivityAction<NoteEditor>
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

    private fun ensureCollectionLoaded() {
        CollectionHelper.instance.getCol(targetContext)
    }
}
