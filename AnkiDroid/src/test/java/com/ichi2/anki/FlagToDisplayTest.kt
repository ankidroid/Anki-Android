//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_NONE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class FlagToDisplayTest {

    @ParameterizedTest
    @CsvSource(
        "false, false",
        "false, true",
        "true, false",
        "false, false"
    )
    fun `is hidden if no flag is set`(toolbarButton: Boolean, fullscreen: Boolean) {
        assertEquals(FLAG_NONE, FlagToDisplay(FLAG_NONE, toolbarButton, fullscreen).get())
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7])
    fun `is hidden if flag is on app bar and fullscreen is disabled`(actualFlag: Int) {
        assertEquals(FLAG_NONE, FlagToDisplay(actualFlag, isOnAppBar = true, isFullscreen = false).get())
    }
}
