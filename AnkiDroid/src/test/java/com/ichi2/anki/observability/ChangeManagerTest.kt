/*
 Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>

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
package com.ichi2.anki.observability

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChanges
import anki.collection.opChanges
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.ExperimentalStdlibApi
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType
import kotlin.reflect.jvm.isAccessible

@OptIn(ExperimentalStdlibApi::class)
@RunWith(AndroidJUnit4::class)
class ChangeManagerTest : RobolectricTest() {

    @Test
    fun `Property is set in ALL object`() {
        val props =
            OpChanges::class.memberProperties.filter { it.returnType.javaType == Boolean::class.java }
        assertThat(props.size, greaterThan(0))
        for (property in props) {
            property.isAccessible = true
            assertThat("Property ${property.name} should be true", property.call(ChangeManager.ALL), equalTo(true))
        }
    }

    @Test
    fun `subscriber exception does not prevent other subscribers from being notified`() {
        val goodSubscriber = mock<ChangeManager.Subscriber>()
        val badSubscriber = object : ChangeManager.Subscriber {
            override fun opExecuted(changes: OpChanges, handler: Any?) {
                throw RuntimeException("Test exception")
            }
        }

        ChangeManager.subscribe(badSubscriber)
        ChangeManager.subscribe(goodSubscriber)

        val testChanges = opChanges { }

        // Should not throw despite bad subscriber
        ChangeManager.notifySubscribers(testChanges, null)

        // Good subscriber should still be called
        verify(goodSubscriber).opExecuted(testChanges, null)
    }
}
