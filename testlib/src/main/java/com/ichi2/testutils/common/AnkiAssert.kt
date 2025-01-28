/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils.common

import org.junit.function.ThrowingRunnable
import timber.log.Timber
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Asserts that [runnable] throws an exception of type [T] when
 * executed. If it does, the exception object is returned. If it does not throw an exception, an
 * [AssertionError] is thrown. If it throws the wrong type of exception, an
 * [AssertionError] is thrown describing the mismatch; the exception that was actually thrown can
 * be obtained by calling `AssertionError.cause`.
 *
 * @param message the identifying message for the [AssertionError]
 * @param T the expected type of the exception
 * @param runnable a function that is expected to throw an exception when executed
 * @return the exception thrown by [runnable]
 */
inline fun <reified T : Throwable> assertThrows(
    message: String? = null,
    runnable: ThrowingRunnable,
): T = org.junit.Assert.assertThrows(message, T::class.java, runnable)

/**
 * Asserts that the given [block] returns `true` within [timeout]
 *
 * Currently used for WebView callbacks which are difficult to listen for
 *
 * @param message message to show on failure
 * @param timeout Duration after which to fail
 * @param sleepDuration Duration to wait and try again. NOTE: Exclusive of time to execute [block]
 * @param block The assertion which should return `true`
 */
fun assertTrueWithTimeout(
    message: String,
    // 10ms is typically sufficient, but I want more leeway for CI.
    // This is a maximum bound and typically will not be hit
    timeout: Duration = 1.seconds,
    sleepDuration: Duration = 10.milliseconds,
    block: () -> Boolean,
) {
    require(timeout.inWholeMilliseconds > 0) { "timeout > 0" }
    require(sleepDuration.inWholeMilliseconds > 0) { "sleepDuration > 0" }

    var totalSleepTime = Duration.ZERO

    while (totalSleepTime < timeout) {
        if (block()) {
            if (totalSleepTime > 0.milliseconds) {
                Timber.v("assertion true after %s", totalSleepTime)
            }
            return
        }

        Timber.v("assertion failed, waiting %s", sleepDuration)
        Thread.sleep(sleepDuration.inWholeMilliseconds)
        totalSleepTime += sleepDuration
    }

    assertTrue("timeout ($timeout): $message", block)
}
