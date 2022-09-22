/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.tests.libanki

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelTest : InstrumentedTest() {

    private val mTestCol = emptyCol

    @After
    fun tearDown() {
        mTestCol.close()
    }

    @Test
    fun bigQuery() {
        assumeTrue(
            "This test is flaky on API29, ignoring",
            Build.VERSION.SDK_INT != Build.VERSION_CODES.Q
        )
        val models = mTestCol.models
        val model = models.all()[0]
        val testString = "test"
        val size = testString.length * 1024 * 1024
        val buf = StringBuilder((size * 1.01).toInt())
        // * 1.01 for padding
        for (i in 0 until 1024 * 1024) {
            buf.append(testString)
        }
        model.put(testString, buf.toString())
        // Buf should be more than 4MB, so at least two chunks from database.
        models.flush()
        // Reload models
        mTestCol.load()
        val newModel = models.all()[0]
        assertEquals(newModel, model)
    }
}
