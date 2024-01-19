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
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.IntentHandler.Companion.getLaunchType
import com.ichi2.anki.IntentHandler.LaunchType
import com.ichi2.anki.services.ReminderService.Companion.getReviewDeckIntent
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
class IntentHandlerTest {
    // COULD_BE_BETTER: We're testing class internals here, would like to see these tests be replaced with
    // higher-level tests at a later date when we better extract dependencies
    @Test
    fun viewIntentReturnsView() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://invalid"))

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.FILE_IMPORT))
    }

    @Test
    fun syncIntentReturnsSync() {
        val intent = Intent("com.ichi2.anki.DO_SYNC")

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.SYNC))
    }

    @Test
    fun reviewIntentReturnsReview() {
        val intent = getReviewDeckIntent(Mockito.mock(Context::class.java), 1)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.REVIEW))
    }

    @Test
    fun mainIntentStartsApp() {
        val intent = Intent(Intent.ACTION_MAIN)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.DEFAULT_START_APP_IF_NEW))
    }

    @Test
    fun textImportIntentReturnsTextImport() {
        // TSV import
        var intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse("content://valid"), "text/tab-separated-values")
        var expected = getLaunchType(intent)
        assertThat(expected, equalTo(LaunchType.TEXT_IMPORT))

        // CSV import
        intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse("content://valid"), "text/comma-separated-values")
        expected = getLaunchType(intent)
        assertThat(expected, equalTo(LaunchType.TEXT_IMPORT))
    }

    @Test
    fun viewWithNoDataPerformsDefaultAction() {
        // #6312 - Smart Launcher double-tap launches us with this. No data at all in the intent
        // so we can only perform the default action
        val intent = Intent(Intent.ACTION_VIEW)

        val expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.DEFAULT_START_APP_IF_NEW))
    }
}
