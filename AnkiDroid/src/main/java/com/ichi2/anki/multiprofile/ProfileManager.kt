/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Manages the creation, loading, and switching of user profiles.
 * Acts as the single source of truth for the current profile state.
 */
class ProfileManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    lateinit var activeProfileContext: Context
        private set

    /**
     * Stores the Registry of all profiles (ID -> Display Name) and the
     * ID of the currently active profile.
     */
    private val globalProfilePrefs by lazy {
        appContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
    }

    private val profileRegistry by lazy { ProfileRegistry(globalProfilePrefs) }

    fun initializeAndLoadActiveProfile() {
        val activeProfileId =
            profileRegistry.getLastActiveProfileId()
                ?: initializeDefaultProfile()

        Timber.i("Initializing profile: ${activeProfileId.value}")
        loadProfileData(activeProfileId)
    }

    private fun initializeDefaultProfile(): ProfileId {
        Timber.i("No active profile found. Setting up Default.")
        val defaultId = ProfileId(DEFAULT_PROFILE_ID)

        val metadata =
            ProfileMetadata(
                displayName = DEFAULT_PROFILE_DISPLAY_NAME,
                isLegacy = true,
            )

        profileRegistry.saveProfile(defaultId, metadata)
        profileRegistry.setLastActiveProfileId(defaultId)

        return defaultId
    }

    fun createNewProfile(displayName: String): ProfileId {
        val newProfileId = generateUniqueProfileId()

        val metadata =
            ProfileMetadata(
                displayName = displayName,
                isLegacy = false,
            )

        profileRegistry.saveProfile(newProfileId, metadata)

        Timber.i("Created new profile: $displayName (${newProfileId.value})")
        return newProfileId
    }

    private fun generateUniqueProfileId(): ProfileId {
        var newId: ProfileId
        var collisionCount = 0
        do {
            if (collisionCount > 0) Timber.w("Profile ID collision detected!")
            val rawId = "p_" + UUID.randomUUID().toString().substring(0, 8)
            newId = ProfileId(rawId)
            collisionCount++
        } while (profileRegistry.contains(newId))
        return newId
    }

    fun switchActiveProfile(newProfileId: ProfileId) {
        Timber.i("Switching profile to ID: ${newProfileId.value}")

        profileRegistry.setLastActiveProfileId(newProfileId)
        triggerAppRestart()
    }

    private fun loadProfileData(profileId: ProfileId) {
        configureWebView(profileId)

        val profileBaseDir = resolveProfileDirectory(profileId)

        try {
            activeProfileContext =
                ProfileContextWrapper.create(
                    context = appContext,
                    profileId = profileId,
                    profileBaseDir = profileBaseDir,
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load profile context for ${profileId.value}")
            throw RuntimeException("Failed to load profile environment", e)
        }

        Timber.d("Profile loaded: ${profileId.value} at ${profileBaseDir.absolutePath}")
    }

    private fun configureWebView(profileId: ProfileId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            CookieManager.getInstance().removeAllCookies(null)
            return
        }

        if (profileId.isDefault()) {
            return
        }

        try {
            WebView.setDataDirectorySuffix(profileId.value)
        } catch (e: Exception) {
            // This usually means the WebView was accessed before the profile was loaded.
            // This represents a potential privacy leak (using default cookies).
            Timber.e(e, "Failed to set WebView directory suffix (WebView already initialized?)")
        }
    }

    /**
     * Resolves the physical file system location for a given profile's data.
     *
     * - **Default Profile:** Returns the application's root data directory (legacy behavior).
     * - **New Profiles:** Returns a subdirectory named after the Profile ID (e.g., `/data/user/0/com.anki/p_a1b2c3d4`).
     *
     * @param profileId The validated identifier of the profile.
     * @return A [File] object representing the root directory for this profile.
     * The directory is guaranteed to be inside the application's private storage.
     */
    private fun resolveProfileDirectory(profileId: ProfileId): File {
        val appDataRoot =
            ContextCompat.getDataDir(appContext)
                ?: appContext.filesDir.parentFile
                ?: throw IllegalStateException("Cannot resolve Application Data Directory")

        return if (profileId.isDefault()) {
            appDataRoot
        } else {
            File(appDataRoot, profileId.value)
        }
    }

    private fun triggerAppRestart() {
        Timber.w("Restarting app to apply profile switch")
        // TODO: Implement process restart logic (e.g. ProcessPhoenix)
    }

    /**
     * Holds the meta-data for a profile.
     * Converted to JSON for storage to allow future extensibility (e.g. avatars, themes).
     */
    data class ProfileMetadata(
        val displayName: String,
        val isLegacy: Boolean = false,
        val createdTimestamp: String = getTimestamp(TimeManager.time),
    ) {
        fun toJson(): String =
            JSONObject()
                .apply {
                    put("displayName", displayName)
                    put("isLegacy", isLegacy)
                    put("created", createdTimestamp)
                }.toString()

        companion object {
            fun fromJson(jsonString: String): ProfileMetadata {
                val json = JSONObject(jsonString)
                return ProfileMetadata(
                    displayName = json.optString("displayName", "Unknown"),
                    isLegacy = json.optBoolean("isLegacy", false),
                    createdTimestamp = json.optString("created", ""),
                )
            }
        }
    }

    /**
     * Internal abstraction for the Global Profile Registry.
     * Handles the JSON serialization/deserialization.
     */
    private class ProfileRegistry(
        private val prefs: SharedPreferences,
    ) {
        fun getLastActiveProfileId(): ProfileId? {
            val id = prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null)
            return id?.let { ProfileId(it) }
        }

        fun setLastActiveProfileId(id: ProfileId) {
            prefs.edit { putString(KEY_LAST_ACTIVE_PROFILE_ID, id.value) }
        }

        fun saveProfile(
            id: ProfileId,
            metadata: ProfileMetadata,
        ) {
            prefs.edit { putString(id.value, metadata.toJson()) }
        }

        fun getProfileMetadata(id: ProfileId): ProfileMetadata? {
            val jsonString = prefs.getString(id.value, null) ?: return null
            return try {
                ProfileMetadata.fromJson(jsonString)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse profile metadata for ${id.value}")
                null
            }
        }

        fun contains(id: ProfileId): Boolean = prefs.contains(id.value)
    }

    companion object {
        const val PROFILE_REGISTRY_FILENAME = "profiles_prefs"
        const val KEY_LAST_ACTIVE_PROFILE_ID = "last_active_profile_id"

        val DEFAULT_PROFILE_ID = ProfileId.DEFAULT.value
        const val DEFAULT_PROFILE_DISPLAY_NAME = "Default"
    }
}
