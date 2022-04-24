/*
 *  Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>
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

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class MaxExecFunctionTest {
    private var mFunction: Runnable? = null

    @Before
    fun before() {
        mFunction = mock(Runnable::class.java)
    }

    @Test
    fun doNotExceedMaxExecs() {
        val m = MaxExecFunction(3, mFunction!!)
        for (i in 0..49) {
            m.exec()
        }
        verify(mFunction, times(3))?.run()
    }

    @Test
    fun onlyOnceForAReference() {
        val ref = Any()
        val m = MaxExecFunction(3, mFunction!!)
        for (i in 0..49) {
            m.execOnceForReference(ref)
        }
        verify(mFunction, times(1))?.run()
    }

    @Test
    fun doNotExceedMaxExecsWithMultipleReferences() {
        val m = MaxExecFunction(3, mFunction!!)
        for (i in 0..9) {
            val ref = Any()
            for (j in 0..9) {
                m.execOnceForReference(ref)
            }
        }
        verify(mFunction, times(3))?.run()
    }
}
