// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.KEY_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.multiprofile.isPhoenixProcess
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
    fun `terminating application unregisters its lifecycle observer`() {
        val application = ApplicationProvider.getApplicationContext<AnkiDroidApp>()
        val lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
        val otherObserver = LifecycleEventObserver { _, _ -> }
        lifecycle.addObserver(otherObserver)
        try {
            val observerCount = lifecycle.observerCount

            application.onTerminate()

            assertEquals(observerCount - 1, lifecycle.observerCount)
            lifecycle.removeObserver(otherObserver)
            assertEquals(observerCount - 2, lifecycle.observerCount)
        } finally {
            lifecycle.removeObserver(otherObserver)
        }
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

    @Test
    fun `phoenix process skips the profile environment`() {
        mockkStatic(::isPhoenixProcess)
        every { isPhoenixProcess() } returns true
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
