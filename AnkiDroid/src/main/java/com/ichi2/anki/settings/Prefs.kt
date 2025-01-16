/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.settings

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.settings.enums.PrefEnum
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO move this to `com.ichi2.anki.preferences`
//  after the UI classes of that package are moved to `com.ichi2.anki.ui.preferences`
object Prefs {
    private val sharedPrefs get() = AnkiDroidApp.sharedPrefs()

    @VisibleForTesting
    val resources get() = AnkiDroidApp.appResources

    @VisibleForTesting
    fun key(
        @StringRes resId: Int,
    ): String = resources.getString(resId)

    @VisibleForTesting
    fun getBoolean(
        @StringRes keyResId: Int,
        defValue: Boolean,
    ): Boolean = sharedPrefs.getBoolean(key(keyResId), defValue)

    @VisibleForTesting
    fun putBoolean(
        @StringRes keyResId: Int,
        value: Boolean,
    ) {
        sharedPrefs.edit { putBoolean(key(keyResId), value) }
    }

    @VisibleForTesting
    fun getString(
        @StringRes keyResId: Int,
        defValue: String?,
    ): String? = sharedPrefs.getString(key(keyResId), defValue)

    @VisibleForTesting
    fun putString(
        @StringRes keyResId: Int,
        value: String?,
    ) {
        sharedPrefs.edit { putString(key(keyResId), value) }
    }

    @VisibleForTesting
    fun getInt(
        @StringRes keyResId: Int,
        defValue: Int,
    ): Int = sharedPrefs.getInt(key(keyResId), defValue)

    @VisibleForTesting
    fun <E> getEnum(
        @StringRes keyResId: Int,
        defaultValue: E,
    ): E where E : Enum<E>, E : PrefEnum {
        val enumClass = defaultValue.javaClass
        val stringValue = getString(keyResId, defaultValue.entryValue)
        return enumClass.enumConstants?.firstOrNull {
            it.entryValue == stringValue
        } ?: defaultValue
    }

    // **************************************** Delegates *************************************** //

    @VisibleForTesting
    fun booleanPref(
        @StringRes keyResId: Int,
        defaultValue: Boolean,
    ): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): Boolean = getBoolean(keyResId, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Boolean,
            ) {
                putBoolean(keyResId, value)
            }
        }

    @VisibleForTesting
    fun stringPref(
        @StringRes keyResId: Int,
        defaultValue: String? = null,
    ): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): String? = getString(keyResId, defaultValue) ?: defaultValue

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: String?,
            ) {
                putString(keyResId, value)
            }
        }

    @VisibleForTesting
    class StringPref(
        @StringRes private val keyResId: Int,
        private val defaultValue: String? = null,
    ) : ReadWriteProperty<Any?, String?> {
        override fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ): String? = getString(keyResId, defaultValue) ?: defaultValue

        override fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: String?,
        ) {
            putString(keyResId, value)
        }
    }

    // ****************************************************************************************** //
    // **************************************** Settings **************************************** //
    // ****************************************************************************************** //

    // ****************************************** General ****************************************** //

    val exitViaDoubleTapBack by booleanPref(R.string.exit_via_double_tap_back_key, false)

    // ****************************************** Sync ****************************************** //

    val isAutoSyncEnabled by booleanPref(R.string.automatic_sync_choice_key, false)
    var username by stringPref(R.string.username_key)
    var hkey by stringPref(R.string.hkey_key)

    // **************************************** Reviewer **************************************** //

    val ignoreDisplayCutout by booleanPref(R.string.ignore_display_cutout_key, false)
    val autoFocusTypeAnswer by booleanPref(R.string.type_in_answer_focus_key, true)

    val frameStyle: FrameStyle
        get() = getEnum(R.string.reviewer_frame_style_key, FrameStyle.CARD)

    val hideSystemBars: HideSystemBars
        get() = getEnum(R.string.hide_system_bars_key, HideSystemBars.NONE)

    // ************************************** Accessibility ************************************* //

    val answerButtonsSize: Int
        get() = getInt(R.string.answer_button_size_preference, 100)

    // ************************************* Developer options ********************************** //

    /**
     * Whether developer options should be shown to the user.
     * True in case [BuildConfig.DEBUG] is true
     * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
     */
    var isDevOptionsEnabled: Boolean
        get() = getBoolean(R.string.dev_options_enabled_by_user_key, false) || BuildConfig.DEBUG
        set(value) = putBoolean(R.string.dev_options_enabled_by_user_key, value)
}
