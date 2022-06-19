/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.libanki.Card
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

// This is difficult to test as Chronometer.mStarted isn't visible
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class AnswerTimerTest {
    lateinit var chronometer: Chronometer

    @Before
    fun init() {
        chronometer = spy(Chronometer(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun disabledTimer() {
        val timer = getTimer()

        val card: Card = mock {
            on { showTimer() } doReturn false
        }

        timer.setupForCard(card)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))

        verify(chronometer).stop()
        verify(chronometer).visibility
        verify(chronometer).visibility = View.INVISIBLE
        verify(chronometer, never()).start()
        // verifyNoMoreInteractions(chronometer) // unable to use: android.view.View.$$robo$$android_view_View$setFlags
    }

    @Test
    fun enabledTimer() {
        val timer = getTimer()

        val card: Card = mock {
            on { showTimer() } doReturn true
            on { timeLimit() } doReturn 12
        }

        Mockito.mockStatic(SystemClock::class.java).use { mocked ->
            mocked.`when`<Long> { SystemClock.elapsedRealtime() }.doReturn(13)

            timer.setupForCard(card)
        }

        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("Time limit should be 12 minutes", timer.limit, equalTo(12))

        verify(chronometer).start()
        verify(chronometer, atLeast(1)).visibility // we call twice due to the else branch
        // already visible: verify(chronometer).visibility = View.VISIBLE
        verify(chronometer, never()).stop()
        verify(chronometer).base = 13
        // verifyNoMoreInteractions(chronometer) // unable to use: android.view.View.$$robo$$android_view_View$setFlags
    }

    @Test
    fun toggle() {
        val timer = getTimer()

        val timerCard: Card = mock {
            on { showTimer() } doReturn true
        }

        val nonTimerCard: Card = mock {
            on { showTimer() } doReturn false
        }

        timer.setupForCard(timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))

        timer.setupForCard(nonTimerCard)

        assertThat("timer should not be enabled", timer.showTimer, equalTo(false))
        assertThat("chronometer should not be visible", chronometer.visibility, equalTo(View.INVISIBLE))

        timer.setupForCard(timerCard)
        assertThat("timer should be enabled", timer.showTimer, equalTo(true))
        assertThat("chronometer should be visible", chronometer.visibility, equalTo(View.VISIBLE))
    }

    @Test
    fun testNoCrashOnEarlyPauseResume() {
        val timer = getTimer()
        // before we call setupForCard
        timer.pause()
        timer.resume()
    }

    @Test
    fun pauseResumeIfEnabled() {
        val timer = getTimer()

        val timerCard: Card = mock {
            on { showTimer() } doReturn true
            on { timeLimit() } doReturn 1000
        }

        timer.setupForCard(timerCard)

        reset(chronometer)
        timer.pause()
        verify(chronometer).stop()
        timer.resume()
        verify(chronometer).start()
    }

    @Test
    fun pauseResumeDoesNotCallStartIfTimeElapsed() {
        val timer = getTimer()

        val timerCard: Card = mock {
            on { showTimer() } doReturn true
            on { timeLimit() } doReturn 1000
            on { timeTaken() } doReturn 1001
        }

        timer.setupForCard(timerCard)

        reset(chronometer)
        timer.pause()
        verify(chronometer).stop()
        timer.resume()
        verify(chronometer, never()).start()
    }

    @Test
    fun cardTimerIsRestartedEvenIfDisabled() {
        // The class is responsible for the pause/resume handling of the card, not just the UI element
        // This may be a candidate for later refactoring

        val timer = getTimer()

        val nonTimerCard: Card = mock {
            on { showTimer() } doReturn false
        }

        timer.setupForCard(nonTimerCard)

        timer.pause()
        verify(nonTimerCard).stopTimer()
        verify(nonTimerCard, never()).resumeTimer()
        timer.resume()
        verify(nonTimerCard).resumeTimer()

        verify(chronometer, never()).start()
    }

    private fun getTimer(): AnswerTimer {
        return AnswerTimer(chronometer)
    }
}
