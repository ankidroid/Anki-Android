/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerCommand.*
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_BLUE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_GREEN
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_NONE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_ORANGE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_PINK
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_PURPLE
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_RED
import com.ichi2.anki.reviewer.CardMarker.Companion.FLAG_TURQUOISE
import com.ichi2.anki.reviewer.CardMarker.FlagDef
import com.ichi2.anki.reviewer.ReviewerUi.ControlBlock
import com.ichi2.libanki.Card
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock

@RunWith(AndroidJUnit4::class)
@KotlinCleanup(
    "is -> equalTo" +
        "when -> whenever"
)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    @Test
    fun doubleTapSetsNone() {
        val viewer = viewer
        viewer.executeCommand(COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(COMMAND_TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, `is`(FLAG_NONE))
    }

    @Test
    fun noneDoesNothing() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_UNSET_FLAG)

        assertThat(viewer.lastFlag, `is`(FLAG_NONE))
    }

    @Test
    fun doubleNoneDoesNothing() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_UNSET_FLAG)
        viewer.executeCommand(COMMAND_UNSET_FLAG)

        assertThat(viewer.lastFlag, `is`(FLAG_NONE))
    }

    @Test
    fun flagCanBeChanged() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(COMMAND_TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, `is`(FLAG_BLUE))
    }

    @Test
    fun unsetUnsets() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(COMMAND_UNSET_FLAG)

        assertThat(viewer.lastFlag, `is`(FLAG_NONE))
    }

    @Test
    fun tapRedFlagSetsRed() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, `is`(FLAG_RED))
    }

    @Test
    fun tapOrangeFlagSetsOrange() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_ORANGE)

        assertThat(viewer.lastFlag, `is`(FLAG_ORANGE))
    }

    @Test
    fun tapGreenFlagSesGreen() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_GREEN)

        assertThat(viewer.lastFlag, `is`(FLAG_GREEN))
    }

    @Test
    fun tapBlueFlagSetsBlue() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, `is`(FLAG_BLUE))
    }

    @Test
    fun tapPinkFlagSetsPink() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_PINK)

        assertThat(viewer.lastFlag, `is`(FLAG_PINK))
    }

    @Test
    fun tapTurquoiseFlagSetsTurquoise() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_TURQUOISE)

        assertThat(viewer.lastFlag, `is`(FLAG_TURQUOISE))
    }

    @Test
    fun tapPurpleFlagSetsPurple() {
        val viewer = viewer

        viewer.executeCommand(COMMAND_TOGGLE_FLAG_PURPLE)

        assertThat(viewer.lastFlag, `is`(FLAG_PURPLE))
    }

    @Test
    fun doubleTapUnsets() {
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_RED)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_ORANGE)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_GREEN)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_BLUE)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_PINK)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_TURQUOISE)
        testDoubleTapUnsets(COMMAND_TOGGLE_FLAG_PURPLE)
    }

    private fun testDoubleTapUnsets(command: ViewerCommand) {
        val viewer = viewer

        viewer.executeCommand(command)
        viewer.executeCommand(command)

        assertThat(command.toString(), viewer.lastFlag, `is`(FLAG_NONE))
    }

    @KotlinCleanup("rename the getter method to createViewer(), remove val")
    private val viewer: CommandTestCardViewer
        get() = CommandTestCardViewer(cardWith(FLAG_NONE))

    private fun cardWith(@Suppress("SameParameterValue") @FlagDef flag: Int): Card {
        val c = mock(Card::class.java)
        val flags = intArrayOf(flag)
        `when`(c.userFlag()).then { flags[0] }
        doAnswer { invocation: InvocationOnMock ->
            flags[0] = invocation.getArgument(0)
            null
        }.`when`(c).setUserFlag(anyInt())
        return c
    }

    private class CommandTestCardViewer(currentCard: Card?) : Reviewer() {
        var lastFlag = 0
            private set

        // we don't have getCol() here and we don't need the additional sound processing.
        override var currentCard: Card?
            get() = super.currentCard
            set(card) {
                mCurrentCard = card
                // we don't have getCol() here and we don't need the additional sound processing.
            }

        override fun setTitle() {
            // Intentionally blank
        }

        override fun performReload() {
            // intentionally blank
        }

        override var controlBlocked: ControlBlock
            get() = ControlBlock.UNBLOCKED
            set(controlBlocked) {
                super.controlBlocked = controlBlocked
            }

        override fun isControlBlocked(): Boolean {
            return controlBlocked !== ControlBlock.UNBLOCKED
        }

        override fun onFlag(card: Card?, @FlagDef flag: Int) {
            lastFlag = flag
            mCurrentCard!!.setUserFlag(flag)
        }

        init {
            this@CommandTestCardViewer.currentCard = currentCard
        }
    }
}
