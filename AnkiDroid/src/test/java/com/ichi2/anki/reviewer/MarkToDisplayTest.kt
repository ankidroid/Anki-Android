//noinspection MissingCopyrightHeader
package com.ichi2.anki.reviewer

import android.view.View
import com.ichi2.anki.R
import com.ichi2.anki.reviewer.MarkToDisplay.HIDDEN
import com.ichi2.anki.reviewer.MarkToDisplay.VISIBLE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class MarkToDisplayTest {

    @Test
    fun `HIDDEN mark is invisible`() {
        assertEquals(View.INVISIBLE, HIDDEN.visibility)
    }

    @Test
    fun `HIDDEN mark has no icon`() {
        assertEquals(null, HIDDEN.icon)
    }

    @Test
    fun `VISIBLE mark is visible`() {
        assertEquals(View.VISIBLE, VISIBLE.visibility)
    }

    @Test
    fun `VISIBLE mark has star icon`() {
        assertEquals(R.drawable.ic_star_white_bordered_24dp, VISIBLE.icon)
    }

    @ParameterizedTest(name = "isFullscreen = {0}")
    @ValueSource(booleans = [false, true])
    fun `mark is visible if not on app bar`(isFullscreen: Boolean) {
        assertEquals(VISIBLE, MarkToDisplay.forState(isCardMarked = true, isOnAppBar = false, isFullscreen))
    }

    @Test
    fun `mark is visible if on app bar and fullscreen enabled`() {
        assertEquals(VISIBLE, MarkToDisplay.forState(isCardMarked = true, isOnAppBar = true, isFullscreen = true))
    }

    @Test
    fun `mark is hidden if on app bar and fullscreen disabled`() {
        assertEquals(HIDDEN, MarkToDisplay.forState(isCardMarked = true, isOnAppBar = true, isFullscreen = false))
    }

    @ParameterizedTest(name = "isOnAppBar = {0}, isFullscreen = {1}")
    @CsvSource(
        "false,false",
        "false,true",
        "true,false",
        "true,true"
    )
    fun `mark is hidden if card not marked`(isOnAppBar: Boolean, isFullscreen: Boolean) {
        assertEquals(HIDDEN, MarkToDisplay.forState(isCardMarked = false, isOnAppBar, isFullscreen))
    }
}
