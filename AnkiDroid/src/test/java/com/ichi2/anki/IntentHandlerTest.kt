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
        var intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://invalid"))
        var expected = getLaunchType(intent)

        assertThat(expected, equalTo(LaunchType.FILE_IMPORT))

        intent = Intent(Intent.ACTION_SEND, Uri.parse("content://invalid"))
        expected = getLaunchType(intent)

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
    fun imageOcclusionIntent() {
        val mimeTypes = listOf("image/jpeg", "image/png")

        for (mimeType in mimeTypes) {
            var intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse("content://valid"), mimeType)
            var expected = getLaunchType(intent)

            assertThat(expected, equalTo(LaunchType.IMAGE_IMPORT))

            intent = Intent(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://valid"))
            intent.type = mimeType
            expected = getLaunchType(intent)

            assertThat(expected, equalTo(LaunchType.IMAGE_IMPORT))
        }
    }

    @Test
    fun textImportIntentReturnsTextImport() {
        testIntentType("content://valid", "text/tab-separated-values", LaunchType.TEXT_IMPORT)
        testIntentType("content://valid", "text/comma-separated-values", LaunchType.TEXT_IMPORT)

        // Test for ACTION_SEND
        testIntentType("content://valid", "text/tab-separated-values", LaunchType.TEXT_IMPORT, Intent.ACTION_SEND)
        testIntentType("content://valid", "text/comma-separated-values", LaunchType.TEXT_IMPORT, Intent.ACTION_SEND)
    }

    private fun testIntentType(data: String, type: String, expected: LaunchType, action: String = Intent.ACTION_VIEW) {
        val intent = Intent(action)
        intent.setDataAndType(Uri.parse(data), type)
        val actual = getLaunchType(intent)
        assertThat(actual, equalTo(expected))
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
