/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.common.time

import android.annotation.SuppressLint
import com.ichi2.anki.common.utils.ObjectForTest

/** Singleton providing an instance of [Time].
 * Used for tests to mock the time provider
 * without forcing the direct dependency on a [Time] instance
 *
 * For later: move this into a DI container
 */
// Only used to add the property [time]
@SuppressLint("DirectSystemTimeInstantiation")
class TimeManagerClass : ObjectForTest<Time>(SystemTime()) {
    val time = value
}

val TimeManager = TimeManagerClass()
