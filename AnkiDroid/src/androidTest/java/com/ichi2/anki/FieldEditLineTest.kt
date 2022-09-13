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
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class FieldEditLineTest : NoteEditorTest() {
    @Test
    fun testSetters() {
        val line = fieldEditLine().apply {
            setContent("Hello", true)
            name = "Name"
            setOrd(5)
        }
        val text = line.editText
        assertEquals(text!!.ord, 5)
        assertEquals(text.text.toString(), "Hello")
        assertEquals(line.name, "Name")
    }

    @Test
    fun testSaveRestore() {
        val toSave = fieldEditLine().apply {
            setContent("Hello", true)
            name = "Name"
            setOrd(5)
        }
        val b = toSave.onSaveInstanceState()

        val restored = fieldEditLine()
        restored.onRestoreInstanceState(b!!)

        val text = restored.editText
        assertEquals(text!!.ord, 5)
        assertEquals(text.text.toString(), "Hello")
        assertEquals(toSave.name, "Name")
    }

    private fun fieldEditLine(): FieldEditLine {
        val reference = AtomicReference<FieldEditLine>()
        activityRule!!.scenario.onActivity { noteEditor: NoteEditor? ->
            reference.set(FieldEditLine(noteEditor!!))
        }
        return reference.get()
    }
}
