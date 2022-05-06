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

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.NotificationChannels
import com.ichi2.anki.NotificationChannels.getId
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
@RequiresApi(Build.VERSION_CODES.O) // getNotificationChannels, NotificationChannel.getId
class NotificationChannelTest : InstrumentedTest() {
    @Rule
    var mRuntimePermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private var mCurrentAPI = -1
    private var mTargetAPI = -1
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

    @SuppressLint("LegacyNullAssertionDetector")
    @Test
    fun testChannelCreation() {
        if (!channelsInAPI()) return

        // onCreate was called in setUp(), so we should have channels now
        val channels = mManager!!.notificationChannels
        for (i in channels.indices) {
            Timber.d("Found channel with id %s", channels[i].id)
        }
        var expectedChannels = NotificationChannels.Channel.values().size
        // If we have channels but have *targeted* pre-26, there is a "miscellaneous" channel auto-defined
        if (mTargetAPI < 26) {
            expectedChannels += 1
        }
        Assert.assertEquals(
            "Incorrect channel count",
            expectedChannels.toLong(),
            channels.size.toLong()
        )
        for (channel in NotificationChannels.Channel.values()) {
            Assert.assertNotNull(
                "There should be a reminder channel",
                mManager!!.getNotificationChannel(getId(channel))
            )
        }
    }
}
