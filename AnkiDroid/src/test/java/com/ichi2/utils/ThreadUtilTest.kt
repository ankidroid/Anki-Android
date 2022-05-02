/*
*  Copyright (c) 2022 Mohd Raghib <raghib.khan76@gmail.com>
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

package com.ichi2.utils

import android.annotation.SuppressLint
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.Test

class ThreadUtilTest {

    @SuppressLint("DirectSystemCurrentTimeMillisUsage")
    @Test(timeout = 5000) // timeout makes sure that test fails if it hangs
    fun sleepTest() {
        val start: Long = System.currentTimeMillis()

        ThreadUtil.sleep(1000) // makes the thread sleep for 1 second

        val end: Long = System.currentTimeMillis()

        assertThat(end - start, greaterThan(1000)) // checking if the time difference between "end" and "start" is greater than the time thread has been put to sleep
    }
}
