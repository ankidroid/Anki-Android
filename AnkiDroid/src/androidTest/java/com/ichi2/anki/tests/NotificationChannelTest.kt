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
import androidx.core.content.edit
import androidx.test.annotation.UiThreadTest
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.Channel
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.test.junit.JUnitAsserter.assertNotNull

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O) // getNotificationChannels, NotificationChannel.getId

@KotlinCleanup("Enable JUnit 5 in androidTest and use JUnit5Asserter to match the standard tests")
class NotificationChannelTest : InstrumentedTest() {
    companion object {
        // amount of time to wait for notification to show up in testNotification
        private const val NOTIFICATION_TEST_IDLE_SECONDS: Long = 5
    }

    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance
    private var currentAPI = -1
    private var targetAPI = -1

    private lateinit var manager: NotificationManager
    private lateinit var notificationIdlingResource: NotificationIdlingResource

    /**
     * Espresso resource that waits for a notification to appear
     * For use in testNotification
     */
    private class NotificationIdlingResource(
        private val notificationManager: NotificationManager,
    ) : IdlingResource {
        @Volatile
        private var callback: IdlingResource.ResourceCallback? = null

        @Volatile
        private var isIdle = false

        override fun getName(): String = "NotificationIdlingResource"

        override fun isIdleNow(): Boolean {
            isIdle = notificationManager.activeNotifications.isNotEmpty()
            Timber.d("Running notification test: waiting for notification to appear...")
            if (isIdle) {
                callback?.onTransitionToIdle()
            }
            return isIdle
        }

        override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
            this.callback = callback
        }
    }

    @Before
    @UiThreadTest
    fun setUp() {
        val targetContext = testContext
        (targetContext.applicationContext as AnkiDroidApp).onCreate()
        currentAPI = sdkVersion
        targetAPI = targetContext.applicationInfo.targetSdkVersion
        manager = targetContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // grant permission to post notifications
        getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant ${targetContext.packageName} android.permission.POST_NOTIFICATIONS",
        )

        notificationIdlingResource = NotificationIdlingResource(manager)
        IdlingRegistry.getInstance().register(notificationIdlingResource)
        IdlingPolicies.setIdlingResourceTimeout(NOTIFICATION_TEST_IDLE_SECONDS, TimeUnit.SECONDS)

        // set minimum amount of due cards to trigger a notification to zero to force a notification
        val preferences = targetContext.sharedPrefs()
        preferences.edit(commit = true) {
            putString(targetContext.getString(R.string.pref_notifications_minimum_cards_due_key), "0")
        }
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(notificationIdlingResource)
    }

    private fun channelsInAPI(): Boolean = currentAPI >= 26

    @Test
    fun testNotification() {
        NotificationService.triggerNotificationFor(testContext)
        Espresso.onIdle()
        val notification = manager.activeNotifications.firstOrNull()

        assertNotNull("Notification was sent", notification)
        assertThat("Notification has correct id", NotificationService.WIDGET_NOTIFY_ID, equalTo(notification!!.id))
    }

    @Test
    fun testChannelCreation() {
        if (!channelsInAPI()) return

        // onCreate was called in setUp(), so we should have channels now
        val channels = manager.notificationChannels
        for (i in channels.indices) {
            Timber.d("Found channel with id %s", channels[i].id)
        }
        var expectedChannels = Channel.entries.size
        // If we have channels but have *targeted* pre-26, there is a "miscellaneous" channel auto-defined
        if (targetAPI < 26) {
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
            greaterThanOrEqualTo(channels.size),
        )
        for (channel in Channel.entries) {
            assertNotNull(
                "There should be a reminder channel",
                manager.getNotificationChannel(channel.id),
            )
        }
    }
}
