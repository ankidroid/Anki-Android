// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerCommand.entries
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import java.util.Arrays
import java.util.stream.Collectors

class MappableBindingTest {
    @Test
    fun equalityTest() {
        val allBindings = getAllBindings()

        assertThat(allBindings, hasItem(unicodeCharacter('@')))
        assertThat(allBindings, hasItem(keyCode(KeyEvent.KEYCODE_1)))
    }

    @Test
    fun inequalityTest() {
        val allBindings = getAllBindings()
        // pick an arbitrary key which is not mapped
        assertThat(allBindings, not(hasItem(unicodeCharacter('l'))))
        assertThat(allBindings, not(hasItem(keyCode(KeyEvent.KEYCODE_L))))
    }

    private fun getAllBindings() =
        Arrays
            .stream(entries.toTypedArray())
            .flatMap { x: ViewerCommand -> x.defaultValue.stream() }
            .collect(Collectors.toList())

    @Suppress("SameParameterValue")
    private fun keyCode(code: Int) = fromBinding(BindingTest.keyCode(code))

    @Suppress("SameParameterValue")
    private fun unicodeCharacter(char: Char) = fromBinding(BindingTest.unicodeCharacter(char))

    private fun fromBinding(binding: Binding): Any = ReviewerBinding(binding, CardSide.BOTH)
}
