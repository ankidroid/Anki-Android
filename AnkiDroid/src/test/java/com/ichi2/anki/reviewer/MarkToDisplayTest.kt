// TODO: header (suppress?)
package com.ichi2.anki.reviewer

import android.view.View
import com.ichi2.anki.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkToDisplayTest {

    @Test
    fun `HIDDEN mark is invisible`() {
        assertEquals(View.INVISIBLE, MarkToDisplay.HIDDEN.visibility)
    }

    @Test
    fun `HIDDEN mark has no icon`() {
        assertEquals(null, MarkToDisplay.HIDDEN.icon)
    }

    @Test
    fun `VISIBLE mark is visible`() {
        assertEquals(View.VISIBLE, MarkToDisplay.VISIBLE.visibility)
    }

    @Test
    fun `VISIBLE mark has star icon`() {
        assertEquals(R.drawable.ic_star_white_bordered_24dp, MarkToDisplay.VISIBLE.icon)
    }
}
