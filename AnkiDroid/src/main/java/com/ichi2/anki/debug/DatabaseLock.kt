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

package com.ichi2.anki.debug

import android.content.Context
import com.ichi2.anki.CollectionHelper
import timber.log.Timber

/** Debug only class which will lock the database  */
object DatabaseLock {
    @JvmStatic
    fun engage(ctx: Context?) {
        val c = CollectionHelper.getInstance().getCol(ctx)
        Timber.w("Toggling database lock")
        c.db.database.beginTransaction()
    }
}
