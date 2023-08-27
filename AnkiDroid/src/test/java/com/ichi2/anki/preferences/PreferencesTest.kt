/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.preferences.Preferences.Companion.getDayOffset
import com.ichi2.anki.preferences.Preferences.Companion.setDayOffset
import com.ichi2.preferences.HeaderPreference
import com.ichi2.testutils.getJavaMethodAsAccessible
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PreferencesTest : RobolectricTest() {
    private lateinit var preferences: Preferences

    @Before
    override fun setUp() {
        super.setUp()
        preferences = Preferences()
        val attachBaseContext = getJavaMethodAsAccessible(
            AppCompatActivity::class.java,
            "attachBaseContext",
            Context::class.java
        )
        attachBaseContext.invoke(preferences, targetContext)
    }

    @Test
    fun testDayOffsetExhaustive() {
        runBlocking {
            for (i in 0..23) {
                setDayOffset(preferences, i)
                assertThat(getDayOffset(), equalTo(i))
            }
        }
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun testDayOffsetExhaustiveV2() {
        runBlocking {
            for (i in 0..23) {
                setDayOffset(preferences, i)
                assertThat(getDayOffset(), equalTo(i))
            }
        }
    }

    /** checks if any of the Preferences fragments throws while being created */
    @Test
    fun fragmentsDoNotThrowOnCreation() {
        val activityScenario = ActivityScenario.launch(Preferences::class.java)

        activityScenario.onActivity { activity ->
            PreferenceTestUtils.getAllPreferencesFragments(activity).forEach {
                activity.supportFragmentManager.commitNow {
                    add(R.id.settings_container, it)
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "ar")
    fun buildHeaderSummary_RTL_Test() {
        assertThat(HeaderPreference.buildHeaderSummary("حساب أنكي ويب", "مزامنة تلقائية"), equalTo("مزامنة تلقائية • حساب أنكي ويب"))
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun setDayOffsetSetsConfig() {
        val offset = runBlocking { getDayOffset() }
        assertThat("Default offset should be 4", offset, equalTo(4))
        runBlocking { setDayOffset(preferences, 2) }
        assertThat("rollover config should be set to new value", col.config.get("rollover") ?: 4, equalTo(2))
    }
}
