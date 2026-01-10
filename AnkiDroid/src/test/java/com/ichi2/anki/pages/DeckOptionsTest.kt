/*
 * Copyright (c) 2026 Harsh Somankar <harshsomankar123@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.pages

import androidx.activity.OnBackPressedCallback
import org.junit.Assert.assertFalse
import org.junit.Test

class DeckOptionsTest {
    @Test
    fun backCallback_isDisabledByDefault() {
        val deckOptions = DeckOptions()

        val callbackField =
            DeckOptions::class.java.getDeclaredField("onBackFromModal")
        callbackField.isAccessible = true

        val callback =
            callbackField.get(deckOptions) as OnBackPressedCallback

        assertFalse(
            "Back callback should be disabled by default",
            callback.isEnabled,
        )
    }
}
