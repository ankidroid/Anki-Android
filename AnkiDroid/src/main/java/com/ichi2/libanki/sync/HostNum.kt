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
package com.ichi2.libanki.sync

import com.ichi2.libanki.Consts

/**
 * The server provides hostNum in the /sync/meta call. All requests after that (including future meta requests)
 * should use that hostNum to construct the sync URL, until a future /sync/meta call advises otherwise.
 *
 * This class is not part of libAnki directly, but abstracts Preference saving to a libAnki context
 *
 * This is defined as an integer to avoid string formatting attacks on the URL.
 * Confirmed to always be an integer or null in AnkiWeb
 * https://github.com/ankidroid/Anki-Android/pull/6004#issuecomment-613731597
 *
 * This should be wiped:
 * * On Logoff
 * * On Change of Sync Server
 *
 * As new user data will likely not be under the same hostNum
 */
open class HostNum(open var hostNum: Int?) {

    open fun reset() {
        hostNum = getDefaultHostNum()
    }

    companion object {
        fun getDefaultHostNum(): Int? {
            return Consts.DEFAULT_HOST_NUM
        }
    }
}
