//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckOptionsTest : RobolectricTest() {
    @Test
    fun changeHardFactor() {
        val col = col

        // Verify that for newly created deck hardFactor is default.
        var hardFactor = col.get_config("hardFactor", 1.2)
        Assert.assertEquals(1.2, hardFactor, 0.01)

        // Modify hard factor.
        col.set_config("hardFactor", 1.0)

        // Verify that hardFactor value has changed.
        hardFactor = col.get_config("hardFactor", 1.2)
        Assert.assertEquals(1.0, hardFactor, 0.01)
    }
}
