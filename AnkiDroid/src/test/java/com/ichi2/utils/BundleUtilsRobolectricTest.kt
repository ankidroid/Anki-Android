/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Shridhar Goel <shridhar.goel@gmail.com>                           *
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

package com.ichi2.utils

import androidx.core.os.bundleOf
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.assertThrows
import com.ichi2.utils.BundleUtils.getSerializableWithCast
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class BundleUtilsRobolectricTest {

    @Test
    fun shouldReturnValueIfCastIsSuccessful() {
        val bundle = bundleOf("Example" to 1)
        assertEquals(bundle.getSerializableWithCast<Int>("Example"), 1)
    }

    @Test
    fun shouldThrowExceptionIfCastIsUnsuccessful() {
        val bundle = bundleOf("Example" to "Value")
        assertThrows<ClassCastException> {
            bundle.getSerializableWithCast<Int>("Example")
        }
    }
}
