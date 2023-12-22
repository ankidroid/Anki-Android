/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.ichi2.anki.AnkiDroidApp

/**
 * Acquire a wake lock and release it after running [block].
 *
 * @param levelAndFlags Combination of wake lock level and flag values defining
 *   the requested behavior of the WakeLock
 * @param tag Your class name (or other tag) for debugging purposes
 * @return The return value of `block`
 *
 * @see android.os.PowerManager.newWakeLock
 */
inline fun <T> withWakeLock(
    levelAndFlags: Int,
    tag: String,
    block: () -> T,
): T {
    val context = AnkiDroidApp.instance
    val wakeLock =
        ContextCompat
            .getSystemService(context, PowerManager::class.java)!!
            .newWakeLock(levelAndFlags, context.packageName + ":" + tag)

    wakeLock.acquire()

    return try {
        block()
    } finally {
        wakeLock.release()
    }
}
