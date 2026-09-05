// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.annotation.SuppressLint
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.permissions.isExternalStorageManager
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.testutils.withManageExternalStorageInManifest
import com.ichi2.testutils.withWritePermissions
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SetupStorageTest : RobolectricTest() {
    private val prefs
        get() = targetContext.sharedPrefs()

    private val collectionPath: String?
        get() = prefs.getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @After
    fun resetCollectionHelperState() {
        CollectionHelper.ankiDroidDirectoryOverride = null
        CollectionHelper.systemStorageFailure = null
    }

    /**
     * Reading the collection path must not silently choose and persist a default:
     * that decision belongs to startup ([ensureCollectionPathSet]).
     */
    @Test
    fun `reading the collection path throws when unset`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        assertFailsWith<StorageNotConfiguredException> {
            CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
        }
    }

    /**
     * A storage failure at startup must not masquerade as the expected 'no collection path set'
     * state: the [SystemStorageException] edge case (OS bug/SD card issue) is reported as itself.
     */
    @Test
    fun `reading the collection path reports a recorded startup storage failure`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        val failure = SystemStorageException.build("simulated getExternalFilesDir failure")
        CollectionHelper.systemStorageFailure = failure

        val thrown =
            assertFailsWith<SystemStorageException> {
                CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
            }
        assertSame(failure, thrown)
    }

    @Test
    fun `reading the collection path returns the stored value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }

        assertEquals(File("/a/collection/path"), CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
    }

    @Test
    fun `the directory override takes precedence over the stored value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/collection/path") }
        CollectionHelper.ankiDroidDirectoryOverride = File("/an/override")

        assertEquals(File("/an/override"), CollectionHelper.getCurrentAnkiDroidDirectory(targetContext))
    }

    @Test
    fun `ensureCollectionPathSet persists a default when unset`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        ensureCollectionPathSet(targetContext)

        assertTrue(!collectionPath.isNullOrEmpty(), "a default collection path should be set")
    }

    @Test
    fun `ensureCollectionPathSet does not overwrite an existing value`() {
        prefs.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, "/a/custom/path") }

        ensureCollectionPathSet(targetContext)

        assertEquals("/a/custom/path", collectionPath)
    }

    // The permission screen is mandatory on Android 10 and below: the folder is fixed
    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted before permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        ensureCollectionPathSet(targetContext)

        assertEquals(publicDirectory, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `Android 10 - public storage is persisted once permissions are granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withWritePermissions { ensureCollectionPathSet(targetContext) }

        assertEquals(publicDirectory, collectionPath)
    }

    // TODO: 13574 - the decision is deferred to the user: the screen may be skipped
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - public storage is persisted before 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest { ensureCollectionPathSet(targetContext) }

        assertEquals(publicDirectory, collectionPath)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `full build - public storage is persisted once 'All files access' is granted`() {
        prefs.edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        withManageExternalStorageInManifest {
            withAllFilesAccess { ensureCollectionPathSet(targetContext) }
        }

        assertEquals(publicDirectory, collectionPath)
    }

    private val publicDirectory: String
        get() = File(Environment.getExternalStorageDirectory(), "AnkiDroid").absolutePath

    /** `MANAGE_EXTERNAL_STORAGE` is granted */
    @SuppressLint("NewApi") // isExternalStorageManager requires R, guaranteed by @Config
    private fun withAllFilesAccess(block: () -> Unit) {
        mockkStatic(::isExternalStorageManager)
        every { isExternalStorageManager() } returns true
        try {
            block()
        } finally {
            unmockkStatic(::isExternalStorageManager)
        }
    }
}
