/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.ui

import android.content.Context
import android.view.MenuItem
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.InvocationTargetException

@RunWith(AndroidJUnit4::class)
class ActionBarOverflowTest {
    @Test
    fun hasValidActionBarReflectionMethod() {
        assertThat(
            "Ensures that there is a valid way to obtain a listener",
            ActionBarOverflow.hasUsableMethod(), equalTo(true)
        )
    }

    @Test
    fun errorsAreBeingThrownCanary() {
        try {
            ActionBarOverflow.setupMethods(ActionBarOverflow::getPrivateMethodOnlyHandleExceptions)
        } catch (e: Error) {
            fail(
                """
    See discussion on #5806
    https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces
    Once this throws, errors are being thrown on a currently greylisted method
                """.trimIndent()
            )
        }
    }

    @Test
    fun testAndroidXMenuItem() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val b = MenuBuilder(targetContext)
        val i = b.add("Test")

        val value = ActionBarOverflow.isActionButton(i)

        assertThat(value, notNullValue())
    }

    @Test
    @Throws(
        ClassNotFoundException::class,
        InstantiationException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class
    )
    fun testAndroidMenuItem() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        val clazz = "com.android.internal.view.menu.MenuBuilder"
        val c = Class.forName(clazz)

        val constructor = c.getConstructor(Context::class.java)

        val mmb = constructor.newInstance(targetContext)

        val add = mmb.javaClass.getMethod("add", CharSequence::class.java)
        add.isAccessible = true

        val mi = add.invoke(mmb, "Add") as MenuItem

        val value = ActionBarOverflow.isActionButton(mi)

        assertThat(value, notNullValue())
    }
}
