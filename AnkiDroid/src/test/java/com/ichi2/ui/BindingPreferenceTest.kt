/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BindingPreferenceTest {

    @Test
    fun serialization_deserialization_returns_same_result() {

        val str = getSampleBindings().toPreferenceString()

        val again = MappableBinding.fromPreferenceString(str)

        assertEquals(str, again.toPreferenceString())
    }

    @Test
    fun test_serialisation_does_not_change() {
        // If this changes, we have introduced a breaking change in the serialization
        //
        val expected = "✅a2|✅ 1|⍝LONG_TAP2|⌨122"

        assertEquals(expected, getSampleBindings().toPreferenceString())
    }

    private fun getSampleBindings(): List<MappableBinding> = listOf(
        MappableBinding(Binding.unicode('a'), CardSide.BOTH),
        MappableBinding(Binding.unicode(' '), CardSide.ANSWER),
        // this one is important: ensure that "|" as a unicode char can't be used
        MappableBinding(Binding.unicode(Binding.FORBIDDEN_UNICODE_CHAR), CardSide.QUESTION),
        MappableBinding(Binding.gesture(Gesture.LONG_TAP), CardSide.BOTH),
        MappableBinding(Binding.keyCode(12), CardSide.BOTH)
    )
}
