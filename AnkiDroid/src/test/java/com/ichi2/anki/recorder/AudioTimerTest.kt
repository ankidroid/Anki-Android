/*
 *  Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.recorder

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class AudioTimerTest {
    private val fakeClock =
        object : TimeSource {
            var currentNanos = 0L

            override fun markNow(): TimeMark =
                object : TimeMark {
                    val startNanos = currentNanos

                    override fun elapsedNow(): Duration = (currentNanos - startNanos).nanoseconds

                    override fun plus(duration: Duration): TimeMark = this

                    override fun minus(duration: Duration): TimeMark = this

                    override fun hasPassedNow(): Boolean = elapsedNow() > Duration.ZERO

                    override fun hasNotPassedNow(): Boolean = !hasPassedNow()
                }

            fun advance(duration: Duration) {
                currentNanos += duration.inWholeNanoseconds
            }
        }

    private fun TestScope.advanceTime(duration: Duration) {
        fakeClock.advance(duration)
        advanceTimeBy(duration.inWholeMilliseconds)
    }

    @Test
    fun `start triggers high frequency UI updates`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val waveTicks = mutableListOf<Unit>()

            val timer =
                AudioTimer(
                    scope = backgroundScope,
                    timeSource = fakeClock,
                    onTimerTick = { timerTicks.add(it) },
                    onAudioTick = { waveTicks.add(Unit) },
                )

            timer.start()

            advanceTime(100.milliseconds)

            assertTrue("UI ticks should fire", timerTicks.size in 5..7)
            assertTrue("Wave ticks should fire", waveTicks.size in 1..3)
            assertEquals(100.milliseconds, timerTicks.last())
        }

    @Test
    fun `notification tick fires exactly once per second`() =
        runTest {
            var notificationCount = 0
            val timer =
                AudioTimer(
                    scope = backgroundScope,
                    timeSource = fakeClock,
                    onTimerTick = {},
                    onAudioTick = {},
                    onNotificationTick = { notificationCount++ },
                )

            timer.start()

            advanceTime(2500.milliseconds)

            assertEquals(2, notificationCount)
        }

    @Test
    fun `resume continues from paused duration`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val timer =
                AudioTimer(
                    scope = backgroundScope,
                    timeSource = fakeClock,
                    onTimerTick = { timerTicks.add(it) },
                    onAudioTick = {},
                )

            timer.start()
            advanceTime(2.seconds)

            timer.pause()
            assertEquals(2.seconds, timerTicks.last())

            advanceTime(5.seconds)

            timer.start()
            advanceTime(1.seconds)

            assertEquals(3.seconds, timerTicks.last())
        }

    @Test
    fun `start with custom duration seeks correctly`() =
        runTest {
            val timerTicks = mutableListOf<Duration>()
            val timer =
                AudioTimer(
                    scope = backgroundScope,
                    timeSource = fakeClock,
                    onTimerTick = { timerTicks.add(it) },
                    onAudioTick = {},
                )

            timer.start(50.seconds)

            advanceTime(16.milliseconds)

            assertEquals(50.seconds + 16.milliseconds, timerTicks.last())
        }

    @Test
    fun `stop resets everything`() =
        runTest {
            var lastDuration: Duration = (-1).seconds
            val timer =
                AudioTimer(
                    scope = backgroundScope,
                    timeSource = fakeClock,
                    onTimerTick = { lastDuration = it },
                    onAudioTick = {},
                )

            timer.start()
            advanceTime(1.seconds)
            assertEquals(1.seconds, lastDuration)

            timer.stop()

            assertEquals(Duration.ZERO, lastDuration)
        }
}
