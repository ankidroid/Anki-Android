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
package com.ichi2.libanki.backend

import android.content.Context
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionV16
import com.ichi2.libanki.DB
import com.ichi2.libanki.utils.Time
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.BackendV1

/**
 * Unused.
 *
 * Signifies that the AnkiDroid backend should be used when accessing the JSON columns in `col`
 * as these have moved to separate tables
 */
class RustDroidV16Backend(private val backendFactory: BackendFactory) : RustDroidBackend(backendFactory) {
    val backend: BackendV1
        get() = backendFactory.backend

    override fun databaseCreationInitializesData(): Boolean = true

    override fun createCollection(context: Context, db: DB, path: String?, server: Boolean, log: Boolean, time: Time): Collection =
        CollectionV16(context, db, path, server, log, time, this)
}
