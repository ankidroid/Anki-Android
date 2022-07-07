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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@KotlinCleanup("is -> equalTo")
@KotlinCleanup("use scope function")
@RunWith(AndroidJUnit4::class)
class FieldEditLineTest : NoteEditorTest() {
    @Test
    fun testSetters() {
        val line = fieldEditLine()

        line.setContent("Hello", true)
        line.name = "Name"
        line.setOrd(5)
        val text = line.editText
        assertThat(text!!.ord, `is`(5))
        assertThat(text.text.toString(), `is`("Hello"))
        assertThat(line.name, `is`("Name"))
    }

    @Test
    fun testSaveRestore() {
        val toSave = fieldEditLine()

        toSave.setContent("Hello", true)
        toSave.name = "Name"
        toSave.setOrd(5)

        val b = toSave.onSaveInstanceState()

        val restored = fieldEditLine()
        restored.onRestoreInstanceState(b!!)

        val text = restored.editText
        assertThat(text!!.ord, `is`(5))
        assertThat(text.text.toString(), `is`("Hello"))
        assertThat(toSave.name, `is`("Name"))
    }

    private fun fieldEditLine(): FieldEditLine {
        val reference = AtomicReference<FieldEditLine>()
        mActivityRule.scenario.onActivity { noteEditor: NoteEditor? ->
            reference.set(FieldEditLine(noteEditor!!))
        }
        return reference.get()
    }
}
