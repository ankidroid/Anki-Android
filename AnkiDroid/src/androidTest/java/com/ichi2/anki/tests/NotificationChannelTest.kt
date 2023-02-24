/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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
package com.ichi2.anki.tests

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.Channel
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.junit.JUnitAsserter.assertNotNull

@RunWith(AndroidJUnit4::class)
@RequiresApi(Build.VERSION_CODES.O) // getNotificationChannels, NotificationChannel.getId

@KotlinCleanup("Enable JUnit 5 in androidTest and use JUnit5Asserter to match the standard tests")
class NotificationChannelTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance
    private var mCurrentAPI = -1
    private var mTargetAPI = -1

    @KotlinCleanup("lateinit")
    private var mManager: NotificationManager? = null

    @Before
    @UiThreadTest
    fun setUp() {
        val targetContext = testContext
        (targetContext.applicationContext as AnkiDroidApp).onCreate()
        mCurrentAPI = sdkVersion
        mTargetAPI = targetContext.applicationInfo.targetSdkVersion
        mManager =
            targetContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun channelsInAPI(): Boolean {
        return mCurrentAPI >= 26
    }

    @Test
    fun testChannelCreation() {
        if (!channelsInAPI()) return

        // onCreate was called in setUp(), so we should have channels now
        val channels = mManager!!.notificationChannels
        for (i in channels.indices) {
            Timber.d("Found channel with id %s", channels[i].id)
        }
        var expectedChannels = Channel.values().size
        // If we have channels but have *targeted* pre-26, there is a "miscellaneous" channel auto-defined
        if (mTargetAPI < 26) {
            expectedChannels += 1
        }

        // Any channels we see that are for "LeakCanary" are okay. They are auto-created on test devices.
        for (channel in channels) {
            if (channel.name.toString().contains("LeakCanary")) {
                expectedChannels += 1
            }
        }
        assertThat(
            "Not as many channels as expected.",
            expectedChannels,
            greaterThanOrEqualTo(channels.size)
        )
        for (channel in Channel.values()) {
            assertNotNull(
                "There should be a reminder channel",
                mManager!!.getNotificationChannel(channel.id)
            )
        }
    }
}
