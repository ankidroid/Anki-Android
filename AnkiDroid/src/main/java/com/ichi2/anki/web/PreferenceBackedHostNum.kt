/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.web

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.libanki.sync.HostNum
import timber.log.Timber

class PreferenceBackedHostNum(hostNum: Int?, private val preferences: SharedPreferences) : HostNum(hostNum) {
    /** Clearing hostNum whenever on log out/changes the server URL should avoid any problems with malicious servers */
    override fun reset() {
        hostNum = getDefaultHostNum()
    }

    override var hostNum: Int?
        get() = getHostNum(preferences)
        set(value) {
            Timber.d("Setting hostnum to %s", value)
            val prefValue = convertToPreferenceValue(value)
            preferences.edit { putString("hostNum", prefValue) }
            super.hostNum = value
        }

    @CheckResult
    private fun convertToPreferenceValue(newHostNum: Int?): String? {
        return newHostNum?.toString()
    }

    companion object {
        fun fromPreferences(preferences: SharedPreferences): PreferenceBackedHostNum {
            val hostNum = getHostNum(preferences)
            return PreferenceBackedHostNum(hostNum, preferences)
        }

        private fun getHostNum(preferences: SharedPreferences): Int? {
            return try {
                val hostNum = preferences.getString("hostNum", null)
                Timber.v("Obtained hostNum: %s", hostNum)
                convertFromPreferenceValue(hostNum)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get hostNum")
                getDefaultHostNum()
            }
        }

        private fun convertFromPreferenceValue(hostNum: String?): Int? {
            return if (hostNum == null) {
                getDefaultHostNum()
            } else {
                try {
                    hostNum.toInt()
                } catch (e: Exception) {
                    Timber.w(e)
                    getDefaultHostNum()
                }
            }
        }
    }
}
