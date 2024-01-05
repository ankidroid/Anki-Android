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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.screenBuilder
import com.ichi2.libanki.Consts
import com.ichi2.utils.HashUtil.hashSetInit
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

private typealias VersionIdentifier = Int
private typealias LegacyVersionIdentifier = Long

object PreferenceUpgradeService {
    fun upgradePreferences(context: Context, previousVersionCode: LegacyVersionIdentifier): Boolean =
        upgradePreferences(context.sharedPrefs(), previousVersionCode)

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
    @JvmStatic // required for mockito for now
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
            const val upgradeVersionPrefKey = "preferenceUpgradeVersion"

            /** Returns all instances of preference upgrade classes */
            private fun getAllInstances(legacyPreviousVersionCode: LegacyVersionIdentifier) = sequence {
                yield(LegacyPreferenceUpgrade(legacyPreviousVersionCode))
                yield(UpdateNoteEditorToolbarPrefs())
                yield(UpgradeGesturesToControls())
                yield(UpgradeDayAndNightThemes())
                yield(UpgradeFetchMedia())
                yield(UpgradeAppLocale())
                yield(RemoveScrollingButtons())
                yield(RemoveAnswerRecommended())
                yield(RemoveBackupMax())
                yield(RemoveInCardsMode())
                yield(RemoveReviewerETA())
                yield(SetShowDeckTitle())
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
                preferences.getInt(upgradeVersionPrefKey, 0)

            internal fun setPreferenceVersion(preferences: SharedPreferences, versionIdentifier: VersionIdentifier) {
                Timber.i("upgrading preference version to '$versionIdentifier'")
                preferences.edit { putInt(upgradeVersionPrefKey, versionIdentifier) }
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
                    preferences.edit { clear() }
                }
                // when upgrading from before 2.5alpha35
                if (previousVersionCode < 20500135) {
                    Timber.i("Old version of Anki - Fixing Zoom")
                    // Card zooming behaviour was changed the preferences renamed
                    val oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100)
                    val oldImageZoom = preferences.getInt("relativeImageSize", 100)
                    preferences.edit {
                        putInt("cardZoom", oldCardZoom)
                        putInt("imageZoom", oldImageZoom)
                    }
                    if (!preferences.getBoolean("useBackup", true)) {
                        preferences.edit { putInt("backupMax", 0) }
                    }
                    preferences.edit {
                        remove("useBackup")
                        remove("intentAdditionInstantAdd")
                    }
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

        fun performUpgrade(preferences: SharedPreferences) {
            Timber.i("Running preference upgrade: ${this.javaClass.simpleName}")
            upgrade(preferences)

            setPreferenceVersion(preferences, this.versionIdentifier)
        }

        protected abstract fun upgrade(preferences: SharedPreferences)

        /**
         * update toolbar buttons with new preferences, when button text empty or null then it adds the index as button text
         */
        internal class UpdateNoteEditorToolbarPrefs : PreferenceUpgrade(4) {
            override fun upgrade(preferences: SharedPreferences) {
                val buttons = getNewToolbarButtons(preferences)

                // update prefs
                preferences.edit {
                    remove("note_editor_custom_buttons")
                    putStringSet("note_editor_custom_buttons", CustomToolbarButton.toStringSet(buttons))
                }
            }

            private fun getNewToolbarButtons(preferences: SharedPreferences): ArrayList<CustomToolbarButton> {
                // get old toolbar prefs
                val set = preferences.getStringSet("note_editor_custom_buttons", hashSetInit<String>(0)) as Set<String?>
                // new list with buttons size
                val buttons = ArrayList<CustomToolbarButton>(set.size)

                // parse fields with separator
                for (s in set) {
                    val fields = s!!.split(Consts.FIELD_SEPARATOR.toRegex(), CustomToolbarButton.KEEP_EMPTY_ENTRIES.coerceAtLeast(0)).toTypedArray()
                    if (fields.size != 3) {
                        continue
                    }

                    val index: Int = try {
                        fields[0].toInt()
                    } catch (e: Exception) {
                        Timber.w(e)
                        continue
                    }

                    // add new button with the index + 1 as button text
                    val visualIndex: Int = index + 1
                    val buttonText = visualIndex.toString()

                    // fields 1 is prefix, fields 2 is suffix
                    buttons.add(CustomToolbarButton(index, buttonText, fields[1], fields[2]))
                }
                return buttons
            }
        }

        internal class UpgradeGesturesToControls : PreferenceUpgrade(5) {
            val oldCommandValues = mapOf(
                Pair(1, ViewerCommand.SHOW_ANSWER),
                Pair(2, ViewerCommand.FLIP_OR_ANSWER_EASE1),
                Pair(3, ViewerCommand.FLIP_OR_ANSWER_EASE2),
                Pair(4, ViewerCommand.FLIP_OR_ANSWER_EASE3),
                Pair(5, ViewerCommand.FLIP_OR_ANSWER_EASE4),
                Pair(8, ViewerCommand.UNDO),
                Pair(9, ViewerCommand.EDIT),
                Pair(10, ViewerCommand.MARK),
                Pair(12, ViewerCommand.BURY_CARD),
                Pair(13, ViewerCommand.SUSPEND_CARD),
                Pair(14, ViewerCommand.DELETE),
                Pair(16, ViewerCommand.PLAY_MEDIA),
                Pair(17, ViewerCommand.EXIT),
                Pair(18, ViewerCommand.BURY_NOTE),
                Pair(19, ViewerCommand.SUSPEND_NOTE),
                Pair(20, ViewerCommand.TOGGLE_FLAG_RED),
                Pair(21, ViewerCommand.TOGGLE_FLAG_ORANGE),
                Pair(22, ViewerCommand.TOGGLE_FLAG_GREEN),
                Pair(23, ViewerCommand.TOGGLE_FLAG_BLUE),
                Pair(38, ViewerCommand.TOGGLE_FLAG_PINK),
                Pair(39, ViewerCommand.TOGGLE_FLAG_TURQUOISE),
                Pair(40, ViewerCommand.TOGGLE_FLAG_PURPLE),
                Pair(24, ViewerCommand.UNSET_FLAG),
                Pair(30, ViewerCommand.PAGE_UP),
                Pair(31, ViewerCommand.PAGE_DOWN),
                Pair(32, ViewerCommand.TAG),
                Pair(33, ViewerCommand.CARD_INFO),
                Pair(34, ViewerCommand.ABORT_AND_SYNC),
                Pair(35, ViewerCommand.RECORD_VOICE),
                Pair(36, ViewerCommand.REPLAY_VOICE),
                Pair(46, ViewerCommand.SAVE_VOICE),
                Pair(37, ViewerCommand.TOGGLE_WHITEBOARD),
                Pair(44, ViewerCommand.CLEAR_WHITEBOARD),
                Pair(45, ViewerCommand.CHANGE_WHITEBOARD_PEN_COLOR),
                Pair(41, ViewerCommand.SHOW_HINT),
                Pair(42, ViewerCommand.SHOW_ALL_HINTS),
                Pair(43, ViewerCommand.ADD_NOTE)
            )

            override fun upgrade(preferences: SharedPreferences) {
                upgradeGestureToBinding(preferences, "gestureSwipeUp", Gesture.SWIPE_UP)
                upgradeGestureToBinding(preferences, "gestureSwipeDown", Gesture.SWIPE_DOWN)
                upgradeGestureToBinding(preferences, "gestureSwipeLeft", Gesture.SWIPE_LEFT)
                upgradeGestureToBinding(preferences, "gestureSwipeRight", Gesture.SWIPE_RIGHT)
                upgradeGestureToBinding(preferences, "gestureLongclick", Gesture.LONG_TAP)
                upgradeGestureToBinding(preferences, "gestureDoubleTap", Gesture.DOUBLE_TAP)
                upgradeGestureToBinding(preferences, "gestureTapTopLeft", Gesture.TAP_TOP_LEFT)
                upgradeGestureToBinding(preferences, "gestureTapTop", Gesture.TAP_TOP)
                upgradeGestureToBinding(preferences, "gestureTapTopRight", Gesture.TAP_TOP_RIGHT)
                upgradeGestureToBinding(preferences, "gestureTapLeft", Gesture.TAP_LEFT)
                upgradeGestureToBinding(preferences, "gestureTapCenter", Gesture.TAP_CENTER)
                upgradeGestureToBinding(preferences, "gestureTapRight", Gesture.TAP_RIGHT)
                upgradeGestureToBinding(preferences, "gestureTapBottomLeft", Gesture.TAP_BOTTOM_LEFT)
                upgradeGestureToBinding(preferences, "gestureTapBottom", Gesture.TAP_BOTTOM)
                upgradeGestureToBinding(preferences, "gestureTapBottomRight", Gesture.TAP_BOTTOM_RIGHT)
                upgradeVolumeGestureToBinding(preferences, "gestureVolumeUp", KeyEvent.KEYCODE_VOLUME_UP)
                upgradeVolumeGestureToBinding(preferences, "gestureVolumeDown", KeyEvent.KEYCODE_VOLUME_DOWN)
            }

            private fun upgradeVolumeGestureToBinding(preferences: SharedPreferences, oldGesturePreferenceKey: String, volumeKeyCode: Int) {
                upgradeBinding(preferences, oldGesturePreferenceKey, keyCode(volumeKeyCode))
            }

            private fun upgradeGestureToBinding(preferences: SharedPreferences, oldGesturePreferenceKey: String, gesture: Gesture) {
                upgradeBinding(preferences, oldGesturePreferenceKey, Binding.gesture(gesture))
            }

            @VisibleForTesting
            internal fun upgradeBinding(preferences: SharedPreferences, oldGesturePreferenceKey: String, binding: Binding) {
                Timber.d("Replacing gesture '%s' with binding", oldGesturePreferenceKey)

                // This exists as a user may have mapped "volume down" to "UNDO".
                // Undo already exists as a key binding, and we don't want to trash this during an upgrade
                if (!preferences.contains(oldGesturePreferenceKey)) {
                    Timber.v("No preference to upgrade")
                    return
                }

                try {
                    replaceBinding(preferences, oldGesturePreferenceKey, binding)
                } finally {
                    Timber.v("removing pref key: '%s'", oldGesturePreferenceKey)
                    // remove the old key
                    preferences.edit { remove(oldGesturePreferenceKey) }
                }
            }

            private fun replaceBinding(preferences: SharedPreferences, oldGesturePreferenceKey: String, binding: Binding) {
                // the preference should be set, but if it's null, then we have nothing to do
                val pref = preferences.getString(oldGesturePreferenceKey, "0") ?: return
                // If the preference doesn't map (for example: it was removed), then nothing to do
                val asInt = pref.toIntOrNull() ?: return
                val command = oldCommandValues[asInt] ?: return

                Timber.i("Moving preference from '%s' to '%s'", oldGesturePreferenceKey, command.preferenceKey)

                // add to the binding_COMMANDNAME preference
                val mappableBinding = MappableBinding(binding, command.screenBuilder(CardSide.BOTH))
                command.addBindingAtEnd(preferences, mappableBinding)
            }
        }
        internal class UpgradeDayAndNightThemes : PreferenceUpgrade(6) {
            override fun upgrade(preferences: SharedPreferences) {
                val dayTheme = preferences.getString("dayTheme", "0")
                val nightTheme = preferences.getString("nightTheme", "0")

                preferences.edit {
                    if (dayTheme == "1") { // plain
                        putString("dayTheme", "2")
                    } else { // light
                        putString("dayTheme", "1")
                    }
                    if (nightTheme == "1") { // dark
                        putString("nightTheme", "4")
                    } else { // black
                        putString("nightTheme", "3")
                    }
                    remove("invertedColors")
                }
            }
        }

        internal class UpgradeFetchMedia : PreferenceUpgrade(9) {
            override fun upgrade(preferences: SharedPreferences) {
                val fetchMediaSwitch = preferences.getBoolean(RemovedPreferences.SYNC_FETCHES_MEDIA, true)
                val status = if (fetchMediaSwitch) "always" else "never"
                preferences.edit {
                    remove(RemovedPreferences.SYNC_FETCHES_MEDIA)
                    putString("syncFetchMedia", status)
                }
            }
        }

        internal class UpgradeAppLocale : PreferenceUpgrade(10) {
            override fun upgrade(preferences: SharedPreferences) {
                fun getLocale(localeCode: String): Locale {
                    // Language separators are '_' or '-' at different times in display/resource fetch
                    val locale: Locale = if (localeCode.contains("_") || localeCode.contains("-")) {
                        try {
                            val localeParts = localeCode.split("[_-]".toRegex(), 2).toTypedArray()
                            Locale(localeParts[0], localeParts[1])
                        } catch (e: ArrayIndexOutOfBoundsException) {
                            Timber.w(e, "getLocale variant split fail, using code '%s' raw.", localeCode)
                            Locale(localeCode)
                        }
                    } else {
                        Locale(localeCode) // guaranteed to be non null
                    }
                    return locale
                }
                // 1. upgrade value from `locale.toString()` to `locale.toLanguageTag()`,
                // because the new API uses language tags
                val languagePrefValue = preferences.getString("language", "")!!
                val languageTag = if (languagePrefValue.isNotEmpty()) {
                    getLocale(languagePrefValue).toLanguageTag()
                } else {
                    null
                }
                preferences.edit {
                    putString("language", languageTag ?: "")
                }
                // 2. Set the locale with the new AndroidX API
                val localeList = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        internal class RemoveScrollingButtons : PreferenceUpgrade(11) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit { remove("scrolling_buttons") }
            }
        }

        internal class RemoveAnswerRecommended : PreferenceUpgrade(12) {
            override fun upgrade(preferences: SharedPreferences) {
                moveControlBindings(preferences, "binding_FLIP_OR_ANSWER_RECOMMENDED", ViewerCommand.FLIP_OR_ANSWER_EASE3.preferenceKey)
                moveControlBindings(preferences, "binding_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED", ViewerCommand.FLIP_OR_ANSWER_EASE4.preferenceKey)
            }

            private fun moveControlBindings(preferences: SharedPreferences, sourcePrefKey: String, destinyPrefKey: String) {
                val sourcePrefValue = preferences.getString(sourcePrefKey, null) ?: return
                val destinyPrefValue = preferences.getString(destinyPrefKey, null)

                val joinedBindings = MappableBinding.fromPreferenceString(destinyPrefValue) + MappableBinding.fromPreferenceString(sourcePrefValue)
                preferences.edit {
                    putString(destinyPrefKey, joinedBindings.toPreferenceString())
                    remove(sourcePrefKey)
                }
            }
        }

        /**
         * Switch from using a single backup option to using separate preferences for
         * daily/weekly/monthly as well as frequency of backups.
         */
        internal class RemoveBackupMax : PreferenceUpgrade(13) {
            override fun upgrade(preferences: SharedPreferences) {
                val legacyValue = preferences.getInt("backupMax", 4)
                preferences.edit {
                    remove("backupMax")
                    putInt("minutes_between_automatic_backups", 30) // 30 minutes default
                    putInt("daily_backups_to_keep", legacyValue)
                    putInt("weekly_backups_to_keep", legacyValue)
                    putInt("monthly_backups_to_keep", legacyValue)
                }
            }
        }

        /** We should have used [anki.config.ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE] */
        internal class RemoveInCardsMode : PreferenceUpgrade(14) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit {
                    remove("inCardsMode")
                }
            }
        }

        internal class RemoveReviewerETA : PreferenceUpgrade(15) {
            override fun upgrade(preferences: SharedPreferences) =
                preferences.edit { remove("showETA") }
        }

        /** default to true for existing users  */
        internal class SetShowDeckTitle : PreferenceUpgrade(16) {
            override fun upgrade(preferences: SharedPreferences) {
                if (!preferences.contains("showDeckTitle")) {
                    preferences.edit { putBoolean("showDeckTitle", true) }
                }
            }
        }
    }
}

object RemovedPreferences {
    const val SYNC_FETCHES_MEDIA = "syncFetchesMedia"
}
