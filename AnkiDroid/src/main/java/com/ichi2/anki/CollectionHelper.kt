// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.CollectionHelper.getCurrentAnkiDroidDirectory
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.isInstrumentationTest
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.storage.StorageDecision
import com.ichi2.preferences.getOrSetString
import timber.log.Timber
import java.io.File
import java.io.IOException

object CollectionHelper {
    /**
     * The preference key for the path to the current AnkiDroid directory
     *
     * This directory contains all AnkiDroid data and media for a given collection
     * Except the Android preferences, cached files and [MetaDB]
     *
     * This can be changed by the Preferences screen
     * to allow a user to access a second collection via the same AnkiDroid app instance.
     *
     * The path also defines the collection that the AnkiDroid API accesses
     */
    const val PREF_COLLECTION_PATH = "deckPath"

    fun getCollectionSize(context: Context): Long? =
        try {
            getCollectionPath(context).length()
        } catch (e: Exception) {
            Timber.e(e, "Error getting collection Length")
            null
        }

    /**
     * Create the AnkiDroid directory if it doesn't exist and add a .nomedia file to it if needed.
     *
     * The AnkiDroid directory is a user preference stored under [PREF_COLLECTION_PATH], and a sensible
     * default is chosen if the preference hasn't been created yet (i.e., on the first run).
     *
     * The presence of a .nomedia file indicates to media scanners that the directory must be
     * excluded from their search. We need to include this to avoid media scanners including
     * media files from the collection.media directory. The .nomedia file works at the directory
     * level, so placing it in the AnkiDroid directory will ensure media scanners will also exclude
     * the collection.media sub-directory.
     *
     * @param dir  Directory to initialize
     * @throws StorageAccessException If no write access to directory
     */
    @Synchronized
    @Throws(StorageAccessException::class)
    fun initializeAnkiDroidDirectory(dir: File) {
        // Create specified directory if it doesn't exit
        if (!dir.exists() && !dir.mkdirs()) {
            throw StorageAccessException("Failed to create AnkiDroid directory $dir")
        }
        if (!dir.canWrite()) {
            throw StorageAccessException("No write access to AnkiDroid directory $dir")
        }
        // Add a .nomedia file to it if it doesn't exist
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile()
            } catch (e: IOException) {
                throw StorageAccessException("Failed to create .nomedia file", e)
            }
        }
    }

    /**
     * Try to access the current AnkiDroid directory
     * @return whether or not dir is accessible
     * @param context to get directory with
     */
    fun isCurrentAnkiDroidDirAccessible(context: Context): Boolean =
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory(context))
            true
        } catch (e: StorageAccessException) {
            Timber.w(e)
            false
        }

    /**
     * @return Returns an array of [File]s reflecting the directories that AnkiDroid can access without storage permissions
     * @see android.content.Context.getExternalFilesDirs
     */
    fun getAppSpecificExternalDirectories(context: Context): List<File> = context.getExternalFilesDirs(null)?.filterNotNull() ?: listOf()

    /**
     * Returns the absolute path to the private AnkiDroid directory under the app-specific, internal storage directory.
     *
     *
     * AnkiDroid can access this directory without permissions, even under Scoped Storage
     * Other apps cannot access this directory, regardless of what permissions they have
     *
     * @param context Used to get the Internal App-Specific directory for AnkiDroid
     * @return Returns the absolute path to the App-Specific Internal AnkiDroid Directory
     */
    fun getAppSpecificInternalAnkiDroidDirectory(context: Context): String = context.filesDir.absolutePath

    /**
     * @return the path to the actual [Collection] file
     *
     * @throws UnsupportedOperationException if the collection is in-memory
     */
    fun getCollectionPath(context: Context) = getCollectionPaths(context).requireDiskBasedCollection().colDb

    /** A temporary override for [getCurrentAnkiDroidDirectory] */
    var ankiDroidDirectoryOverride: File? = null

    /**
     * Whether the user has chosen where the collection is stored.
     *
     * TODO: real implementation based on whether [PREF_COLLECTION_PATH] is set.
     *  TODO: What is a user revokes full storage?
     *  This currently returns [StorageDecision.Decided], so callers that gate on it are no-ops.
     */
    fun storageDecision(): StorageDecision = storageDecisionTestOverride ?: StorageDecision.Decided

    /**
     * @return the absolute path to the AnkiDroid directory.
     *
     * @throws SystemStorageException if `getExternalFilesDir` returns null
     */
    fun getCurrentAnkiDroidDirectory(context: Context): File =
        getCurrentAnkiDroidDirectoryOptionalContext(context.sharedPrefs()) { context }

    fun getCollectionPaths(context: Context): CollectionFiles = CollectionFiles.FolderBasedCollection(getCurrentAnkiDroidDirectory(context))

    // TODO: Duplicates collection.mediaFolder
    fun getMediaDirectory(context: Context) = getCollectionPaths(context).requireMediaFolder()

    /**
     * An accessor which makes [Context] optional in the case that [PREF_COLLECTION_PATH] is set
     *
     * @return the absolute path to the AnkiDroid directory.
     */
    // This uses a lambda as we typically depends on the `lateinit` appContext
    // If we remove all Android references, we get a significant unit test speedup
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun getCurrentAnkiDroidDirectoryOptionalContext(
        preferences: SharedPreferences,
        context: () -> Context,
    ): File =
        if (isInstrumentationTest) {
            // create an "androidTest" directory inside the current collection directory which contains the test data
            // "/AnkiDroid/androidTest" would be a new collection path
            val currentCollectionDirectory =
                preferences.getOrSetString(PREF_COLLECTION_PATH) {
                    getDefaultAnkiDroidDirectory(context()).absolutePath
                }
            File(
                currentCollectionDirectory,
                "androidTest",
            )
        } else {
            ankiDroidDirectoryOverride
                ?: File(
                    preferences.getOrSetString(PREF_COLLECTION_PATH) {
                        getDefaultAnkiDroidDirectory(
                            context(),
                        ).absolutePath
                    },
                )
        }

    /** Test-only override for [storageDecision]. @see ankiDroidDirectoryOverride */
    @VisibleForTesting
    var storageDecisionTestOverride: StorageDecision? = null
}
