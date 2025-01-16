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

import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
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
    fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = sharedPrefs.getBoolean(key, defValue)

    @VisibleForTesting
    fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        sharedPrefs.edit { putBoolean(key, value) }
    }

    @VisibleForTesting
    fun getString(
        key: String,
        defValue: String?,
    ): String? = sharedPrefs.getString(key, defValue)

    @VisibleForTesting
    fun putString(
        key: String,
        value: String?,
    ) {
        sharedPrefs.edit { putString(key, value) }
    }

    @VisibleForTesting
    fun getInt(
        key: String,
        defValue: Int,
    ): Int = sharedPrefs.getInt(key, defValue)

    @VisibleForTesting
    fun <E> getEnum(
        key: String,
        defaultValue: E,
    ): E where E : Enum<E>, E : PrefEnum {
        val enumClass = defaultValue.javaClass
        val stringValue = getString(key, defaultValue.entryValue)
        return enumClass.enumConstants?.firstOrNull {
            it.entryValue == stringValue
        } ?: defaultValue
    }

    // **************************************** Delegates *************************************** //

    @VisibleForTesting
    fun booleanPref(
        key: String,
        defaultValue: Boolean,
    ): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): Boolean = getBoolean(key, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Boolean,
            ) {
                putBoolean(key, value)
            }
        }

    @VisibleForTesting
    fun stringPref(
        key: String,
        defaultValue: String? = null,
    ): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): String? = getString(key, defaultValue) ?: defaultValue

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: String?,
            ) {
                putString(key, value)
            }
        }

    // ****************************************************************************************** //
    // **************************************** Settings **************************************** //
    // ****************************************************************************************** //

    // ****************************************** Sync ****************************************** //

    val isAutoSyncEnabled by booleanPref(PrefKey.AUTO_SYNC, false)
    var username by stringPref(PrefKey.USERNAME)
    var hkey by stringPref(PrefKey.HKEY)

    // **************************************** Reviewer **************************************** //

    val ignoreDisplayCutout by booleanPref(PrefKey.IGNORE_DISPLAY_CUTOUT, false)
    val autoFocusTypeAnswer by booleanPref(PrefKey.AUTO_FOCUS_TYPE_ANSWER, true)

    val frameStyle: FrameStyle
        get() = getEnum(PrefKey.FRAME_STYLE, FrameStyle.CARD)

    val hideSystemBars: HideSystemBars
        get() = getEnum(PrefKey.HIDE_SYSTEM_BARS, HideSystemBars.NONE)

    // ************************************** Accessibility ************************************* //

    val answerButtonsSize: Int
        get() = getInt(PrefKey.ANSWER_BUTTON_SIZE, 100)

    // ************************************* Developer options ********************************** //

    /**
     * Whether developer options should be shown to the user.
     * True in case [BuildConfig.DEBUG] is true
     * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
     */
    var isDevOptionsEnabled: Boolean
        get() = getBoolean(PrefKey.DEV_OPTIONS_ENABLED, false) || BuildConfig.DEBUG
        set(value) = putBoolean(PrefKey.DEV_OPTIONS_ENABLED, value)
}
