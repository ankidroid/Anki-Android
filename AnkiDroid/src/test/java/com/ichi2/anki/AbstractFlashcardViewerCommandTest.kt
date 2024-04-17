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
import com.ichi2.anki.cardviewer.ViewerRefresh
import com.ichi2.libanki.Card
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class AbstractFlashcardViewerCommandTest : RobolectricTest() {
    @Test
    fun doubleTapSetsNone() {
        val viewer = createViewer()
        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE.code))
    }

    @Test
    fun noneDoesNothing() {
        val viewer = createViewer()

        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE.code))
    }

    @Test
    fun doubleNoneDoesNothing() {
        val viewer = createViewer()

        viewer.executeCommand(UNSET_FLAG)
        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE.code))
    }

    @Test
    fun flagCanBeChanged() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, equalTo(Flag.BLUE.code))
    }

    @Test
    fun unsetUnsets() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)
        viewer.executeCommand(UNSET_FLAG)

        assertThat(viewer.lastFlag, equalTo(Flag.NONE.code))
    }

    @Test
    fun tapRedFlagSetsRed() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_RED)

        assertThat(viewer.lastFlag, equalTo(Flag.RED.code))
    }

    @Test
    fun tapOrangeFlagSetsOrange() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_ORANGE)

        assertThat(viewer.lastFlag, equalTo(Flag.ORANGE.code))
    }

    companion object {
        val updateCard: ViewerRefresh = ViewerRefresh(queues = true, note = true, card = true)
    }

    @Test
    fun testRefreshIfRequired() {
        // Create an ActivityController instance for AbstractFlashcardViewer
        Robolectric.buildActivity(AbstractFlashcardViewerTest.NonAbstractFlashcardViewer::class.java).use { controller ->
            // Case 1: Activity is resuming, refreshRequired is not null
            with(controller.create().start().get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = true)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 2: Activity is not resuming, lifecycle is at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.resume() // Ensure the activity is resumed before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is set to null
                assertThat(refreshRequired, nullValue())
            }

            // Case 3: Activity is not resuming, lifecycle is not at least RESUMED, refreshRequired is not null
            with(controller.get()) {
                refreshRequired = updateCard
                controller.pause() // Ensure the activity is paused before calling refreshIfRequired
                refreshIfRequired(isResuming = false)
                // Assert that refreshRequired is not set to null
                assertThat(refreshRequired, notNullValue())
            }
        }
    }

    @Test
    fun tapGreenFlagSesGreen() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_GREEN)

        assertThat(viewer.lastFlag, equalTo(Flag.GREEN.code))
    }

    @Test
    fun tapBlueFlagSetsBlue() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_BLUE)

        assertThat(viewer.lastFlag, equalTo(Flag.BLUE.code))
    }

    @Test
    fun tapPinkFlagSetsPink() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_PINK)

        assertThat(viewer.lastFlag, equalTo(Flag.PINK.code))
    }

    @Test
    fun tapTurquoiseFlagSetsTurquoise() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_TURQUOISE)

        assertThat(viewer.lastFlag, equalTo(Flag.TURQUOISE.code))
    }

    @Test
    fun tapPurpleFlagSetsPurple() {
        val viewer = createViewer()

        viewer.executeCommand(TOGGLE_FLAG_PURPLE)

        assertThat(viewer.lastFlag, equalTo(Flag.PURPLE.code))
    }

    @Test
    fun doubleTapUnsets() {
        testDoubleTapUnsets(TOGGLE_FLAG_RED)
        testDoubleTapUnsets(TOGGLE_FLAG_ORANGE)
        testDoubleTapUnsets(TOGGLE_FLAG_GREEN)
        testDoubleTapUnsets(TOGGLE_FLAG_BLUE)
        testDoubleTapUnsets(TOGGLE_FLAG_PINK)
        testDoubleTapUnsets(TOGGLE_FLAG_TURQUOISE)
        testDoubleTapUnsets(TOGGLE_FLAG_PURPLE)
    }

    private fun testDoubleTapUnsets(command: ViewerCommand) {
        val viewer = createViewer()

        viewer.executeCommand(command)
        viewer.executeCommand(command)

        assertThat(command.toString(), viewer.lastFlag, equalTo(Flag.NONE.code))
    }

    private fun createViewer(): CommandTestCardViewer {
        return CommandTestCardViewer(cardWith(Flag.NONE.code))
    }

    private fun cardWith(@Suppress("SameParameterValue") flag: Int): Card {
        val c = mock(Card::class.java)
        val flags = intArrayOf(flag)
        whenever(c.userFlag()).then { flags[0] }
        doAnswer { invocation: InvocationOnMock ->
            flags[0] = invocation.getArgument(0)
            null
        }.whenever(c).setUserFlag(anyInt())
        return c
    }

    private class CommandTestCardViewer(private var currentCardOverride: Card?) : Reviewer() {
        var lastFlag = 0
            private set

        override var currentCard: Card?
            get() = currentCardOverride
            set(card) {
                // we don't have getCol() here and we don't need the additional sound processing.
                currentCardOverride = card
            }

        override fun performReload() {
            // intentionally blank
        }

        override fun onFlag(card: Card?, flag: Flag) {
            lastFlag = flag.code
            currentCard!!.setUserFlag(flag.code)
        }
    }
}
