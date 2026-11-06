// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** Advances Robolectric's main looper by [duration], running tasks scheduled within that time. */
fun idleMainLooper(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration.toJavaDuration())
