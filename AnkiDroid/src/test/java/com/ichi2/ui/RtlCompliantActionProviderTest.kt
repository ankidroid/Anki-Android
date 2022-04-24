/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
class RtlCompliantActionProviderTest {
    @Test
    fun test_unwrapContext_will_get_activity() {
        val a = Activity()
        val c: Context = ContextWrapper(
            ContextThemeWrapper(
                ContextWrapper(
                    a
                ),
                0
            )
        )
        val provider = RtlCompliantActionProvider(c)
        assertEquals(provider.mActivity, a)
    }

    @Test
    @KotlinCleanup("assertThrows<>")
    fun test_unwrapContext_will_throw_on_no_activity() {
        val a = Application()
        val c: Context = ContextWrapper(
            ContextThemeWrapper(
                ContextWrapper(
                    a
                ),
                0
            )
        )
        try {
            RtlCompliantActionProvider(c)
        } catch (e: Exception) {
            assertThat(e, instanceOf(ClassCastException::class.java))
            return
        }
        fail("unwrapContext should have thrown a ClassCastException, because the base context is not an activity")
    }
}
