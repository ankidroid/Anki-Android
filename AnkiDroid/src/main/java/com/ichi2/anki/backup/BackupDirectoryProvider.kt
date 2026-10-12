// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.servicelayer.BackupManager
import timber.log.Timber
import java.io.File

/**
 * Resolves the directory that backups are written to.
 *
 * If the user has selected a custom backup folder and it is writable, that folder is
 * used. Otherwise the collection-default backup folder is returned so that backups
 * always succeed even if the custom folder has been removed or its permission revoked.
 */
object BackupDirectoryProvider {
    /** SharedPreferences key for the user-selected backup directory. */
    const val PREF_KEY = "customBackupPath"

    /**
     * Returns the directory backups should be written to.
     *
     * @param collection the collection whose default backup folder is used as fallback.
     * @param prefs the SharedPreferences holding [PREF_KEY]; defaults to the app prefs.
     * @return a guaranteed-to-exist, writable directory.
     */
    fun resolve(collection: Collection, prefs: SharedPreferences): File {
        val custom = prefs.getString(PREF_KEY, null)
        if (!custom.isNullOrBlank()) {
            val dir = File(custom)
            if (ensureUsable(dir)) {
                return dir
            }
            Timber.w("Custom backup directory %s is not usable; falling back", custom)
        }
        val fallback = BackupManager.getBackupDirectoryFromCollection(collection.colDb)
        ensureUsable(fallback)
        return fallback
    }

    /** Convenience overload using the app-wide [SharedPreferences]. */
    fun resolve(context: Context, collection: Collection): File =
        resolve(collection, context.sharedPrefs())

    /**
     * Persists the user-selected backup directory. Pass {@code null} to clear it and
     * reset to the collection default.
     */
    fun setCustomPath(context: Context, path: String?) {
        context.sharedPrefs().edit {
            if (path.isNullOrBlank()) {
                remove(PREF_KEY)
            } else {
                putString(PREF_KEY, File(path).absolutePath)
            }
        }
    }

    /**
     * @return true if [dir] exists (or could be created) and is writable.
     */
    private fun ensureUsable(dir: File): Boolean {
        if (!dir.exists() && !dir.mkdirs()) {
            return false
        }
        return dir.isDirectory && dir.canWrite()
    }
}
