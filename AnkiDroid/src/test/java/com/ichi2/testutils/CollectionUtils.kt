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
package com.ichi2.testutils

import android.database.sqlite.SQLiteDatabaseLockedException
import com.ichi2.libanki.Collection
import com.ichi2.utils.KotlinCleanup
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@KotlinCleanup("use mockito-kotlin after DB.java is converted")
object CollectionUtils {
    fun lockDatabase(collection: Collection) {
        val db = collection.db
        val spy = spy(db)

        doThrow(SQLiteDatabaseLockedException::class.java).whenever(spy).execute(any())
        doThrow(SQLiteDatabaseLockedException::class.java).whenever(spy).execute(any(), any())

        val spiedDb = spy(spy.database)
        whenever(spy.database).thenReturn(spiedDb)
        doThrow(SQLiteDatabaseLockedException::class.java).whenever(spiedDb).beginTransaction()

        collection.db = spy
    }
}
