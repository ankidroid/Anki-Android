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
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.KEY_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class AnkiDroidAppTest {
    private val frameworkBase: Context
        get() = ApplicationProvider.getApplicationContext<AnkiDroidApp>().baseContext

    private val profileRegistry: SharedPreferences
        get() = frameworkBase.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        unmockkAll()
    }

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
    fun `application keeps the framework base context`() {
        val app = ApplicationProvider.getApplicationContext<AnkiDroidApp>()

        assertNull(ProfileManager.attachError)
        assertEquals(
            "android.app.ContextImpl",
            app.baseContext.javaClass.name,
            "ActivityThread.handleReceiver casts the application's base context to ContextImpl",
        )
    }

    @Test
    fun `storage follows a non-default profile`() {
        profileRegistry.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, "p_routed") }

        val app = startApplication()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) {
            putString("key", "profile value")
        }

        val profileDir = File(frameworkBase.dataDir, "p_routed")
        assertTrue(File(frameworkBase.dataDir, "shared_prefs/profile_p_routed_settings.xml").exists())
        listOf(
            app.filesDir,
            app.cacheDir,
            app.codeCacheDir,
            app.noBackupFilesDir,
            app.getDatabasePath("collection.db"),
            app.getDir("textures", Context.MODE_PRIVATE),
        ).forEach { assertTrue("$it is outside $profileDir", it.startsWith(profileDir)) }
    }

    @Test
    fun `ACRA process skips the profile environment`() {
        mockkStatic(::isAcraSenderProcess)
        every { isAcraSenderProcess() } returns true
        profileRegistry.edit(commit = true) { clear() }

        val app = startApplication()

        assertNull(profileRegistry.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))
        assertEquals(frameworkBase.filesDir, app.filesDir)
    }

    private fun startApplication(): AnkiDroidApp =
        AttachableApp().apply {
            attach(
                object : ContextWrapper(frameworkBase) {
                    override fun getApplicationContext(): Context? = null
                },
            )
        }

    private class AttachableApp : AnkiDroidApp() {
        fun attach(base: Context) = attachBaseContext(base)
    }
}
