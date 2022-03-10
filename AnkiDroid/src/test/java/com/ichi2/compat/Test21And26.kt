/*
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.compat

import org.junit.After
import org.junit.Before
import org.junit.runners.Parameterized
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.doReturn

/**
 * Allows to test with CompatV21 and V26.
 * In particular it allows to test version of the code that uses [Files] and [Path] classes.
 * And versions that must restrict themselves to [File].
 */
open class Test21And26(
    open val compat: Compat,
    /** Used in the "Test Results" Window */
    @Suppress("unused") private val unitTestDescription: String
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{1}")

        fun data(): Iterable<Array<Any>> = sequence {
            yield(arrayOf(CompatV21(), "CompatV21"))
            yield(arrayOf(CompatV26(), "CompatV26"))
        }.asIterable()
    }

    val isV21: Boolean
        get() = compat is CompatV21
    val isV26: Boolean
        get() = compat is CompatV26

    lateinit var mocked: MockedStatic<CompatHelper>

    @Before
    open fun setup() {
        mocked = Mockito.mockStatic(CompatHelper::class.java)
        mocked.`when`<Compat> { CompatHelper.getCompat() }.doReturn(compat)
    }

    @After
    fun tearDown() {
        mocked.close()
    }
}
