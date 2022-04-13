/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

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

package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.ichi2.anki.IntentHandler.LaunchType;
import com.ichi2.anki.services.ReminderService;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class IntentHandlerTest {
    // COULD_BE_BETTER: We're testing class internals here, would like to see these tests be replaced with
    // higher-level tests at a later date when we better extract dependencies

    @Test
    public void viewIntentReturnsView() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("content://invalid"));

        LaunchType expected = IntentHandler.getLaunchType(intent);

        assertThat(expected, is(LaunchType.FILE_IMPORT));
    }

    @Test
    public void syncIntentReturnsSync() {
        Intent intent = new Intent("com.ichi2.anki.DO_SYNC");

        LaunchType expected = IntentHandler.getLaunchType(intent);

        assertThat(expected, is(LaunchType.SYNC));
    }

    @Test
    public void reviewIntentReturnsReview() {
        Intent intent = ReminderService.getReviewDeckIntent(mock(Context.class), 1);

        LaunchType expected = IntentHandler.getLaunchType(intent);

        assertThat(expected, is(LaunchType.REVIEW));
    }

    @Test
    public void mainIntentStartsApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);

        LaunchType expected = IntentHandler.getLaunchType(intent);

        assertThat(expected, is(LaunchType.DEFAULT_START_APP_IF_NEW));
    }

    @Test
    public void viewWithNoDataPerformsDefaultAction() {
        // #6312 - Smart Launcher double-tap launches us with this. No data at all in the intent
        // so we can only perform the default action
        Intent intent = new Intent(Intent.ACTION_VIEW);

        LaunchType expected = IntentHandler.getLaunchType(intent);

        assertThat(expected, is(LaunchType.DEFAULT_START_APP_IF_NEW));
    }
}
