// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backup

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.BackupManager
import com.ichi2.testutils.BackendEmulatingOpenConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupDirectoryProviderTest : RobolectricTest() {

    private val prefs: SharedPreferences get() = targetContext.sharedPrefs()

    @Test
    fun `unset preference returns collection default directory`() {
        prefs.edit().remove(BackupDirectoryProvider.PREF_KEY).commit()
        val col = CollectionManager.getColUnsafe()
        val expected = BackupManager.getBackupDirectoryFromCollection(col.colDb)
        assertEquals(expected, BackupDirectoryProvider.resolve(col, prefs))
    }

    @Test
    fun `custom writable directory is honored`() {
        val custom =
            File(targetContext.cacheDir, "custom-backups-${System.nanoTime()}").apply {
                assertTrue(mkdirs())
            }
        prefs.edit().putString(BackupDirectoryProvider.PREF_KEY, custom.absolutePath).commit()
        val col = CollectionManager.getColUnsafe()
        assertEquals(custom, BackupDirectoryProvider.resolve(col, prefs))
    }

    @Test
    fun `non-writable custom directory falls back to collection default`() {
        val blocked =
            File(targetContext.cacheDir, "blocked-${System.nanoTime()}").apply {
                assertTrue(mkdirs())
                assertTrue(setWritable(false))
            }
        prefs.edit().putString(BackupDirectoryProvider.PREF_KEY, blocked.absolutePath).commit()
        val col = CollectionManager.getColUnsafe()
        val expected = BackupManager.getBackupDirectoryFromCollection(col.colDb)
        val resolved = BackupDirectoryProvider.resolve(col, prefs)
        assertFalse(resolved == blocked)
        assertEquals(expected, resolved)
    }

    @Test
    fun `setCustomPath normalizes to absolute path and clears on null`() {
        BackupDirectoryProvider.setCustomPath(targetContext, "relative/path/backups")
        val stored = prefs.getString(BackupDirectoryProvider.PREF_KEY, null)
        assertEquals(File("relative/path/backups").absolutePath, stored)
        BackupDirectoryProvider.setCustomPath(targetContext, null)
        assertEquals(null, prefs.getString(BackupDirectoryProvider.PREF_KEY, null))
    }
}
