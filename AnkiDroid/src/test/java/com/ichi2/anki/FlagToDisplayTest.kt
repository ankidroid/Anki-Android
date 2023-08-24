//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_NONE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlagToDisplayTest {

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
}
