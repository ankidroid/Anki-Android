/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.servicelayer

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.web.CustomSyncServer
import timber.log.Timber

private typealias VersionIdentifier = Int
private typealias LegacyVersionIdentifier = Long

object PreferenceUpgradeService {
    @JvmStatic
    fun upgradePreferences(context: Context?, previousVersionCode: LegacyVersionIdentifier): Boolean =
        upgradePreferences(AnkiDroidApp.getSharedPrefs(context), previousVersionCode)

    /** @return Whether any preferences were upgraded */
    internal fun upgradePreferences(preferences: SharedPreferences, previousVersionCode: LegacyVersionIdentifier): Boolean {
        val pendingPreferenceUpgrades = PreferenceUpgrade.getPendingUpgrades(preferences, previousVersionCode)

        pendingPreferenceUpgrades.forEach {
            it.performUpgrade(preferences)
        }

        return pendingPreferenceUpgrades.isNotEmpty()
    }

    /**
     * Specifies that no preference upgrades need to happen.
     * Typically because the app has been run for the first time, or the preferences
     * have been deleted
     */
    @JvmStatic
    fun setPreferencesUpToDate(preferences: SharedPreferences) {
        Timber.i("Marking preferences as up to date")
        PreferenceUpgrade.setPreferenceToLatestVersion(preferences)
    }

    abstract class PreferenceUpgrade private constructor(val versionIdentifier: VersionIdentifier) {
        /*
        To add a new preference upgrade:
          * yield a new class from getAllInstances (do not use `legacyPreviousVersionCode` in the constructor)
          * Implement the upgrade() method
          * Set mVersionIdentifier to 1 more than the previous versionIdentifier
          * Run tests in PreferenceUpgradeServiceTest
         */

        companion object {
            /** A version code where the value doesn't matter as we're not using the result */
            private const val IGNORED_LEGACY_VERSION_CODE = 0L

            /** Returns all instances of preference upgrade classes */
            internal fun getAllInstances(legacyPreviousVersionCode: LegacyVersionIdentifier) = sequence<PreferenceUpgrade> {
                yield(LegacyPreferenceUpgrade(legacyPreviousVersionCode))
                yield(UpgradeVolumeButtonsToBindings())
                yield(RemoveLegacyMediaSyncUrl())
            }

            /** Returns a list of preference upgrade classes which have not been applied */
            fun getPendingUpgrades(preferences: SharedPreferences, legacyPreviousVersionCode: LegacyVersionIdentifier): List<PreferenceUpgrade> {
                val currentPrefVersion: VersionIdentifier = getPreferenceVersion(preferences)

                return getAllInstances(legacyPreviousVersionCode).filter {
                    it.versionIdentifier > currentPrefVersion
                }.toList()
            }

            /** Sets the preference version such that no upgrades need to be applied */
            fun setPreferenceToLatestVersion(preferences: SharedPreferences) {
                val versionWhichRequiresNoUpgrades = getLatestVersion()
                setPreferenceVersion(preferences, versionWhichRequiresNoUpgrades)
            }

            internal fun getPreferenceVersion(preferences: SharedPreferences) =
                preferences.getInt("preferenceUpgradeVersion", 0)

            internal fun setPreferenceVersion(preferences: SharedPreferences, versionIdentifier: VersionIdentifier) {
                Timber.i("upgrading preference version to '$versionIdentifier'")
                preferences.edit { putInt("preferenceUpgradeVersion", versionIdentifier) }
            }

            /** Returns the collection of all preference version numbers */
            @VisibleForTesting
            fun getAllVersionIdentifiers(): Sequence<VersionIdentifier> =
                getAllInstances(IGNORED_LEGACY_VERSION_CODE).map { it.versionIdentifier }

            /**
             * @return the latest "version" of the preferences
             * If the preferences are set to this version, then no upgrades will take place
             */
            private fun getLatestVersion(): VersionIdentifier = getAllVersionIdentifiers().maxOrNull() ?: 0
        }

        /** Handles preference upgrades before 2021-08-01,
         * upgrades were detected via a version code comparison
         * rather than comparing a preference value
         */
        private class LegacyPreferenceUpgrade(val previousVersionCode: LegacyVersionIdentifier) : PreferenceUpgrade(1) {
            override fun upgrade(preferences: SharedPreferences) {
                if (!needsLegacyPreferenceUpgrade(previousVersionCode)) {
                    return
                }

                Timber.i("running upgradePreferences()")
                // clear all prefs if super old version to prevent any errors
                if (previousVersionCode < 20300130) {
                    Timber.i("Old version of Anki - Clearing preferences")
                    preferences.edit().clear().apply()
                }
                // when upgrading from before 2.5alpha35
                if (previousVersionCode < 20500135) {
                    Timber.i("Old version of Anki - Fixing Zoom")
                    // Card zooming behaviour was changed the preferences renamed
                    val oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100)
                    val oldImageZoom = preferences.getInt("relativeImageSize", 100)
                    preferences.edit().putInt("cardZoom", oldCardZoom).apply()
                    preferences.edit().putInt("imageZoom", oldImageZoom).apply()
                    if (!preferences.getBoolean("useBackup", true)) {
                        preferences.edit().putInt("backupMax", 0).apply()
                    }
                    preferences.edit().remove("useBackup").apply()
                    preferences.edit().remove("intentAdditionInstantAdd").apply()
                }
                FullScreenMode.upgradeFromLegacyPreference(preferences)
            }

            fun needsLegacyPreferenceUpgrade(previous: Long): Boolean = previous < CHECK_PREFERENCES_AT_VERSION

            companion object {
                /**
                 * The latest package version number that included changes to the preferences that requires handling. All
                 * collections being upgraded to (or after) this version must update preferences.
                 *
                 * #9309 Do not modify this variable - it no longer works.
                 *
                 * Instead, add an unconditional check for the old preference before the call to
                 * "needsPreferenceUpgrade", and perform the upgrade.
                 */
                const val CHECK_PREFERENCES_AT_VERSION = 20500225
            }
        }

        @VisibleForTesting
        internal class UpgradeVolumeButtonsToBindings : PreferenceUpgrade(2) {
            override fun upgrade(preferences: SharedPreferences) {
                upgradeVolumeGestureToKeyBind(preferences, "gestureVolumeUp", KeyEvent.KEYCODE_VOLUME_UP)
                upgradeVolumeGestureToKeyBind(preferences, "gestureVolumeDown", KeyEvent.KEYCODE_VOLUME_DOWN)
            }

            @VisibleForTesting
            internal fun upgradeVolumeGestureToKeyBind(preferences: SharedPreferences, oldGesturePreferenceKey: String, volumeKeyCode: Int) {
                Timber.d("Replacing gesture '%s' with binding", oldGesturePreferenceKey)

                // This exists as a user may have mapped "volume down" to "UNDO".
                // Undo already exists as a key binding, and we don't want to trash this during an upgrade
                if (!preferences.contains(oldGesturePreferenceKey)) {
                    Timber.v("No preference to upgrade")
                    return
                }

                try {
                    replaceGestureWithBinding(preferences, oldGesturePreferenceKey, volumeKeyCode)
                } finally {
                    Timber.v("removing pref key: '%s'", oldGesturePreferenceKey)
                    // remove the old key
                    preferences.edit { remove(oldGesturePreferenceKey) }
                }
            }

            private fun replaceGestureWithBinding(preferences: SharedPreferences, oldGesturePreferenceKey: String, volumeKeyCode: Int) {
                // the preference should be set, but if it's null, then we have nothing to do
                val gesture = preferences.getString(oldGesturePreferenceKey, "0") ?: return
                // If the preference doesn't map (for example: it was removed), then nothing to do
                val asInt = gesture.toIntOrNull() ?: return
                val command = ViewerCommand.fromInt(asInt) ?: return

                if (command == ViewerCommand.COMMAND_NOTHING) {
                    return
                }

                Timber.i("Moving preference from '%s' to '%s'", oldGesturePreferenceKey, command.preferenceKey)

                // add to the binding_COMMANDNAME preference
                val binding = MappableBinding(keyCode(volumeKeyCode), MappableBinding.Screen.Reviewer(CardSide.BOTH))
                command.addBindingAtEnd(preferences, binding)
            }
        }

        fun performUpgrade(preferences: SharedPreferences) {
            Timber.i("Running preference upgrade: ${this.javaClass.simpleName}")
            upgrade(preferences)

            setPreferenceVersion(preferences, this.versionIdentifier)
        }

        protected abstract fun upgrade(preferences: SharedPreferences)

        /**
         * msync is a legacy URL which does not use the hostnum for load balancing
         * It was the default value in the "custom sync server" preference
         */
        internal class RemoveLegacyMediaSyncUrl : PreferenceUpgrade(3) {
            override fun upgrade(preferences: SharedPreferences) {
                val mediaSyncUrl = CustomSyncServer.getMediaSyncUrl(preferences) ?: return
                if (mediaSyncUrl.startsWith("https://msync.ankiweb.net")) {
                    preferences.edit { remove(CustomSyncServer.PREFERENCE_CUSTOM_MEDIA_SYNC_URL) }
                }
            }
        }
    }
}
