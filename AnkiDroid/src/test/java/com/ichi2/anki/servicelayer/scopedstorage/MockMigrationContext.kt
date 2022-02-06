/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer.scopedstorage

@Suppress("unused", "MemberVisibilityCanBePrivate")
class MockMigrationContext : MigrateUserData.MigrationContext() {
    /** set [logExceptions] to populate this property */
    val exceptions = mutableListOf<Exception>()
    var logExceptions: Boolean = false
    val progress = mutableListOf<NumberOfBytes>()

    override fun reportError(context: MigrateUserData.Operation, ex: Exception) {
        if (!logExceptions) {
            throw ex
        }
        exceptions.add(ex)
    }

    override fun reportProgress(transferred: NumberOfBytes) {
        progress.add(transferred)
    }
}
