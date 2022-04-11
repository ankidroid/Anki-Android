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
import com.ichi2.anki.reviewer.CardMarker
import com.ichi2.anki.reviewer.CardMarker.FlagDef
import com.ichi2.anki.reviewer.ReviewerUi.ControlBlock
import com.ichi2.libanki.Card
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock

@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    @Test
    fun doubleTapSetsNone() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_NONE))
    }

    @Test
    fun noneDoesNothing() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_NONE))
    }

    @Test
    fun doubleNoneDoesNothing() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG)
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_NONE))
    }

    @Test
    fun flagCanBeChanged() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_BLUE))
    }

    @Test
    fun unsetUnsets() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        viewer.executeCommand(ViewerCommand.COMMAND_UNSET_FLAG)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_NONE))
    }

    @Test
    fun tapRedFlagSetsRed() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_RED))
    }

    @Test
    fun tapOrangeFlagSetsOrange() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_ORANGE))
    }

    @Test
    fun tapGreenFlagSesGreen() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_GREEN))
    }

    @Test
    fun tapBlueFlagSetsBlue() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_BLUE))
    }

    @Test
    fun tapPinkFlagSetsPink() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_PINK)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_PINK))
    }

    @Test
    fun tapTurquoiseFlagSetsTurquoise() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_TURQUOISE)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_TURQUOISE))
    }

    @Test
    fun tapPurpleFlagSetsPurple() {
        val viewer = viewer
        viewer.executeCommand(ViewerCommand.COMMAND_TOGGLE_FLAG_PURPLE)
        MatcherAssert.assertThat(viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_PURPLE))
    }

    @Test
    fun doubleTapUnsets() {
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_RED)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_ORANGE)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_GREEN)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_BLUE)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_PINK)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_TURQUOISE)
        testDoubleTapUnsets(ViewerCommand.COMMAND_TOGGLE_FLAG_PURPLE)
    }

    private fun testDoubleTapUnsets(command: ViewerCommand) {
        val viewer = viewer
        viewer.executeCommand(command)
        viewer.executeCommand(command)
        MatcherAssert.assertThat(command.toString(), viewer.lastFlag, Matchers.`is`(CardMarker.FLAG_NONE))
    }

    private val viewer: CommandTestCardViewer
        get() = CommandTestCardViewer(cardWith(CardMarker.FLAG_NONE))

    private fun cardWith(@FlagDef flag: Int): Card {
        val c = Mockito.mock(Card::class.java)
        val flags = intArrayOf(flag)
        Mockito.`when`(c.userFlag()).then { flags[0] }
        Mockito.doAnswer { invocation: InvocationOnMock ->
            flags[0] = invocation.getArgument(0)
            null
        }.`when`(c).setUserFlag(ArgumentMatchers.anyInt())
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
            this.currentCard = currentCard
        }
    }
}
