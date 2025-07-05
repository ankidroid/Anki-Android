/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.common.assertThrows
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotetypesTest : JvmTest() {
    @Test
    fun `getSingleNotetypeOfNotes - multiple`() {
        val notes = addNotes(2)
        val result = col.notetypes.getSingleNotetypeOfNotes(notes.map { it.id })
        assertThat(result, equalTo(notes.first().notetype.id))
    }

    @Test
    fun `getSingleNotetypeOfNotes - valid`() {
        val note = addNotes(1).single()
        val result = col.notetypes.getSingleNotetypeOfNotes(listOf(note.id))
        assertThat(result, equalTo(note.notetype.id))
    }

    @Test
    fun `getSingleNotetypeOfNotes - no input`() {
        val result = assertThrows<BackendInvalidInputException> { col.notetypes.getSingleNotetypeOfNotes(emptyList()) }
        assertThat(result.message, equalTo("no note id provided"))
    }

    @Test
    fun `getSingleNotetypeOfNotes - invalid input`() {
        val noteIds = listOf<Long>(1)
        val result = assertThrows<BackendNotFoundException> { col.notetypes.getSingleNotetypeOfNotes(noteIds) }
        assertThat(
            result.message,
            equalTo("Your database appears to be in an inconsistent state. Please use the Check Database action. No such note: '1'"),
        )
    }

    @Test
    fun `getSingleNotetypeOfNotes - one invalid`() {
        val noteIds = listOf(1, addNotes(1).single().id)
        val result = assertThrows<BackendNotFoundException> { col.notetypes.getSingleNotetypeOfNotes(noteIds) }
        assertThat(
            result.message,
            equalTo("Your database appears to be in an inconsistent state. Please use the Check Database action. No such note: '1'"),
        )
    }

    @Test
    fun `getSingleNotetypeOfNotes - mixed`() {
        val basicNote = addNotes(1).single()
        val clozeNote = addClozeNote("{{c1::aa}}")
        val result =
            assertThrows<BackendInvalidInputException> { col.notetypes.getSingleNotetypeOfNotes(listOf(basicNote.id, clozeNote.id)) }
        assertThat(result.message, equalTo("Please select notes from only one note type."))
    }
}
