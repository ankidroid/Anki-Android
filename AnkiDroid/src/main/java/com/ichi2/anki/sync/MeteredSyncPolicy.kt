/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.sync

import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered

/**
 * Single source of truth for the "warn the user before syncing on a metered connection"
 * preference. All reads of [Prefs.allowSyncOnMeteredConnections] should go through here
 * so that the gating policy lives in one place.
 */
object MeteredSyncPolicy {
    /** True when sync should be blocked/prompted because the network is metered. */
    fun shouldBlock(): Boolean = !Prefs.allowSyncOnMeteredConnections && isActiveNetworkMetered()

    /** Persist the user's "don't ask again" choice from the warning dialog. */
    fun setAlwaysAllow(allow: Boolean) {
        Prefs.allowSyncOnMeteredConnections = allow
    }
}
