/*
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
package com.ichi2.anki.common.utils

import timber.log.Timber

/** make default HTML / JS debugging true for debug build and disable for unit/android tests
 * isRunningAsUnitTest checks if we are in debug or testing environment by checking if org.junit.Test class
 * is imported.
 * https://stackoverflow.com/questions/28550370/how-to-detect-whether-android-app-is-running-ui-test-with-espresso
 */
val isRunningAsUnitTest: Boolean
    get() {
        try {
            Class.forName("org.junit.Test")
        } catch (ignored: ClassNotFoundException) {
            Timber.d("isRunningAsUnitTest: %b", false)
            return false
        }
        Timber.d("isRunningAsUnitTest: %b", true)
        return true
    }
