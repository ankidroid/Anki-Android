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

package com.ichi2.anki.tests;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.test.annotation.UiThreadTest;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.compat.CompatHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import timber.log.Timber;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class NotificationChannelTest extends InstrumentedTest {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    private int mCurrentAPI = -1;
    private int mTargetAPI = -1;
    private NotificationManager mManager = null;

    @Before
    @UiThreadTest
    public void setUp() {
        Context targetContext = getTestContext();
        ((AnkiDroidApp)targetContext.getApplicationContext()).onCreate();
        mCurrentAPI = CompatHelper.getSdkVersion();
        mTargetAPI = targetContext.getApplicationInfo().targetSdkVersion;
        mManager = (NotificationManager)targetContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private boolean channelsInAPI() {
        return mCurrentAPI >= 26;
    }

    @Test
    public void testChannelCreation() {
        if (!channelsInAPI()) return;

        // onCreate was called in setUp(), so we should have channels now
        List<NotificationChannel> channels = mManager.getNotificationChannels();
        for (int i = 0; i < channels.size(); i++) {
            Timber.d("Found channel with id %s", channels.get(i).getId());
        }

        int expectedChannels = NotificationChannels.Channel.values().length;
        // If we have channels but have *targeted* pre-26, there is a "miscellaneous" channel auto-defined
        if (mTargetAPI < 26) {
            expectedChannels += 1;
        }
        assertEquals("Incorrect channel count", expectedChannels, channels.size());

        for (NotificationChannels.Channel channel : NotificationChannels.Channel.values()) {
            assertNotNull("There should be a reminder channel",
                    mManager.getNotificationChannel(NotificationChannels.getId(channel)));
        }
    }
}
