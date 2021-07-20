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
package com.ichi2.anki.reviewer

import android.view.KeyEvent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.Test

class MappableBindingTest {
    @Test
    fun equalityTest() {
        val allBindings = PeripheralCommand.getDefaultCommands()
            .map { x -> x.binding }
            .map(this::fromBinding)
            .toList()

        assertThat(allBindings, hasItem(unicodeCharacter('@')))
        assertThat(allBindings, hasItem(keyCode(KeyEvent.KEYCODE_1)))
    }

    @Suppress("SameParameterValue")
    private fun keyCode(code: Int) = fromBinding(BindingTest.keyCode(code))

    @Suppress("SameParameterValue")
    private fun unicodeCharacter(char: Char) = fromBinding(BindingTest.unicodeCharacter(char))

    private fun fromBinding(binding: Binding): Any {
        return MappableBinding(binding, CardSide.BOTH)
    }
}
