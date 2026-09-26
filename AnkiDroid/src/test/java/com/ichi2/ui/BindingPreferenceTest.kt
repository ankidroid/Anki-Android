// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BindingPreferenceTest {
    @Test
    fun serialization_deserialization_returns_same_result() {
        val str = getSampleBindings().toPreferenceString()

        val again = ReviewerBinding.fromPreferenceString(str)

        assertEquals(str, again.toPreferenceString())
    }

    @Test
    fun test_serialisation_does_not_change() {
        // If this changes, we have introduced a breaking change in the serialization
        //
        val expected = "1/r✅a2|r✅ 1|r⍝DOUBLE_TAP2|r⌨122"

        assertEquals(expected, getSampleBindings().toPreferenceString())
    }

    private fun getSampleBindings(): List<MappableBinding> =
        listOf(
            ReviewerBinding(Binding.unicode('a'), CardSide.BOTH),
            ReviewerBinding(Binding.unicode(' '), CardSide.ANSWER),
            // this one is important: ensure that "|" as a unicode char can't be used
            ReviewerBinding(Binding.unicode(Binding.FORBIDDEN_UNICODE_CHAR), CardSide.QUESTION),
            ReviewerBinding(Binding.gesture(Gesture.DOUBLE_TAP), CardSide.BOTH),
            ReviewerBinding(Binding.keyCode(12), CardSide.BOTH),
        )
}
