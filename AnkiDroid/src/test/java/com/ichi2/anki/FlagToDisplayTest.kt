//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_BLUE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_GREEN
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_NONE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_ORANGE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_PINK
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_PURPLE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_RED
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_TURQUOISE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FlagToDisplayTest {

    @ParameterizedTest
    @ValueSource(ints = [FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE])
    fun `is hidden if flag is on app bar and fullscreen is disabled`(actualFlag: Int) {
        assertEquals(FLAG_NONE, FlagToDisplay(actualFlag, isOnAppBar = true, isFullscreen = false).get())
    }

    @ParameterizedTest
    @ValueSource(ints = [FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE])
    fun `is not hidden if flag is not on app bar and fullscreen is disabled`(actualFlag: Int) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = false, isFullscreen = false).get())
    }

    @ParameterizedTest
    @ValueSource(ints = [FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE])
    fun `is not hidden if flag is not on app bar and fullscreen is enabled`(actualFlag: Int) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = false, isFullscreen = true).get())
    }

    @ParameterizedTest
    @ValueSource(ints = [FLAG_NONE, FLAG_RED, FLAG_ORANGE, FLAG_GREEN, FLAG_BLUE, FLAG_PINK, FLAG_TURQUOISE, FLAG_PURPLE])
    fun `is not hidden if flag is on app bar and fullscreen is enabled`(actualFlag: Int) {
        assertEquals(actualFlag, FlagToDisplay(actualFlag, isOnAppBar = true, isFullscreen = true).get())
    }
}
