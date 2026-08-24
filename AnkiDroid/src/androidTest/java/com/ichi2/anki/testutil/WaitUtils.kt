// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.TestUtils
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Polls until [condition] returns true, syncing with the instrumentation between attempts.
 * Fails the test with [message] if the condition does not hold within [timeout].
 */
fun waitUntil(
    timeout: Duration = 10.seconds,
    message: () -> String,
    condition: () -> Boolean,
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + timeout.inWholeMilliseconds
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        instrumentation.waitForIdleSync()
        SystemClock.sleep(50)
    }
    fail(message())
}

/**
 * Polls until an activity of type [T] is resumed.
 * Fails the test if [T] does not resume within [timeout].
 */
inline fun <reified T : Activity> awaitResumedActivity(timeout: Duration = 10.seconds) =
    waitUntil(
        timeout,
        message = { "Timed out waiting for ${T::class.java.simpleName}; resumed = ${TestUtils.activityInstance}" },
    ) { TestUtils.activityInstance is T }
