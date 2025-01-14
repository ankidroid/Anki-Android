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
import com.ichi2.anki.settings.enums.SettingEnum

object Settings {
    private val prefs by lazy { AnkiDroidApp.sharedPrefs() }

    @VisibleForTesting
    fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = prefs.getBoolean(key, defValue)

    private fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        prefs.edit { putBoolean(key, value) }
    }

    @VisibleForTesting
    fun getString(
        key: String,
        defValue: String?,
    ): String? = prefs.getString(key, defValue)

    @VisibleForTesting
    fun getInt(
        key: String,
        defValue: Int,
    ): Int = prefs.getInt(key, defValue)

    @VisibleForTesting
    fun <E> getEnum(
        key: String,
        defaultValue: E,
    ): E where E : Enum<E>, E : SettingEnum {
        val enumClass = defaultValue.javaClass
        val stringValue = getString(key, defaultValue.entryValue)
        return enumClass.enumConstants?.firstOrNull {
            it.entryValue == stringValue
        } ?: defaultValue
    }

    // ****************************************** Sync ****************************************** //

    val isAutoSyncEnabled: Boolean
        get() = getBoolean(SettingKey.AUTO_SYNC, false)

    val username: String
        get() = getString(SettingKey.USERNAME, "") ?: ""

    // **************************************** Reviewer **************************************** //

    val frameStyle: FrameStyle
        get() = getEnum(SettingKey.FRAME_STYLE, FrameStyle.CARD)

    // ************************************** Accessibility ************************************* //

    val answerButtonsSize: Int
        get() = getInt(SettingKey.ANSWER_BUTTON_SIZE, 100)

    // ************************************* Developer options ********************************** //

    var isDevOptionsEnabled: Boolean
        get() = getBoolean(SettingKey.DEV_OPTIONS_ENABLED, false) || BuildConfig.DEBUG
        set(value) = putBoolean(SettingKey.DEV_OPTIONS_ENABLED, value)
}
