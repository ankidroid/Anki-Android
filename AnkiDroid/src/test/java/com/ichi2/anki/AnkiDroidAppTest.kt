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
package com.ichi2.anki

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AnkiDroidAppTest {
    @Test
    fun reportingDoesNotThrowException() {
        assertDoesNotThrow { sendExceptionReport("Test", "AnkiDroidAppTest") }
    }

    @Test
    fun reportingWithNullMessageDoesNotFail() {
        val message: String? = null
        // It's meant to be non-null, but it's developer-defined, and we don't want a crash in the reporting dialog
        //noinspection ConstantConditions
        assertDoesNotThrow { sendExceptionReport(message, "AnkiDroidAppTest") }
    }

    @Test
    fun `application runs on the profile context`() {
        val app = ApplicationProvider.getApplicationContext<AnkiDroidApp>()

        assertNull(ProfileManager.attachError)
        assertTrue(
            "attachBaseContext must install the profile context",
            app.baseContext is ProfileContextWrapper,
        )
    }

    @Test
    fun `preferences are namespaced for a non-default profile`() {
        val profileId = ProfileId("p_namespaced")
        val base = ApplicationProvider.getApplicationContext<AnkiDroidApp>().baseContext
        base
            .getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(ProfileManager.KEY_LAST_ACTIVE_PROFILE_ID, profileId.value) }

        val attached = assertNotNull(ProfileManager.createOrNull(base)).activeProfileContext as ProfileContextWrapper
        attached.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) {
            putString("key", "profile value")
        }

        val namespaced = File(base.dataDir, "shared_prefs/profile_${profileId.value}_settings.xml")
        assertTrue("Expected a namespaced prefs file at $namespaced", namespaced.exists())
    }
}
