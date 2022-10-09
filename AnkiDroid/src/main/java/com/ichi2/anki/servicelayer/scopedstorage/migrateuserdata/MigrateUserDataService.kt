/*
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata

import android.content.Intent
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.servicelayer.ScopedStorageService

class MigrateUserDataService : android.app.Service() {

    /**
     * Thread executing the migration.
     */
    lateinit var thread: Thread

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val source = intent!!.getStringExtra(ScopedStorageService.PREF_MIGRATION_SOURCE)
        val destination = intent.getStringExtra(ScopedStorageService.PREF_MIGRATION_DESTINATION)
        val migrateUserData = MigrateUserData.createInstance(
            UserDataMigrationPreferences.createInstance(
                source!!,
                destination!!
            )
        )
        thread = Thread {
            migrateUserData.run()
            stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    // TODO: Allow to fetch information on the state of the migration
    override fun onBind(intent: Intent?): Nothing? = null

    override fun onDestroy() {
        super.onDestroy()
        showThemedToast(this, getString(R.string.migration_done), shortLength = false)
    }
}
