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

import android.content.Context
import android.content.SharedPreferences
import com.ichi2.anki.customSyncBase
import timber.log.Timber

object CustomSyncServer {
    // Used by legacy syncing code
    fun getCollectionSyncUrlIfSetAndEnabledOrNull(preferences: SharedPreferences): String? {
        return joinedUrl(preferences, "sync")
    }

    // Used by legacy syncing code
    fun getMediaSyncUrlIfSetAndEnabledOrNull(preferences: SharedPreferences): String? {
        return joinedUrl(preferences, "msync")
    }

    private fun joinedUrl(preferences: SharedPreferences, suffix: String): String? {
        return customSyncBase(preferences)?.let {
            val sep = if (it.last() != '/') {
                "/"
            } else {
                ""
            }
            "${it}${sep}$suffix/"
        }
    }

    fun handleSyncServerPreferenceChange(context: Context) {
        Timber.i("Sync Server Preferences updated.")
        // #4921 - if any of the preferences change, we should reset the HostNum.
        // This is because different servers use different HostNums for data mappings.
        HostNumFactory.getInstance(context).reset()
    }
}
