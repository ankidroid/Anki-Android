/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.sync

import com.ichi2.libanki.sync.Syncer.ConnectionResultType
import com.ichi2.utils.KotlinCleanup

class CustomSyncServerUrlException(
    val url: String,
    @KotlinCleanup("See if ex can be made non-null") ex: IllegalArgumentException?
) : RuntimeException(getMessage(url), ex) {
    override fun getLocalizedMessage(): String {
        // Janky. Connection uses this as a string to return, which is switched on to determine the message in DeckPicker
        return ConnectionResultType.CUSTOM_SYNC_SERVER_URL.toString()
    }

    companion object {
        private fun getMessage(url: String): String {
            return "Invalid Custom Sync Server URL: $url"
        }
    }
}
