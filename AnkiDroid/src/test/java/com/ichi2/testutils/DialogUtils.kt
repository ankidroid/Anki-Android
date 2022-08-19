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

package com.ichi2.testutils

import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.getListAdapter
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertNotNull

/**
 * Extension method to obtain the items from a [MaterialDialog]
 * This uses reflection to access the `PlainListDialogAdapter`'s `items` constructor property
 * https://github.com/afollestad/material-dialogs/blob/709cfee9b45257af7ab4c428e4850bee38a075e7/core/src/main/java/com/afollestad/materialdialogs/internal/list/PlainListDialogAdapter.kt#L54
 */
val MaterialDialog.items: ArrayList<CharSequence>
    get() {
        @Suppress("UNCHECKED_CAST")
        return this.getListAdapter()?.let { adapter ->
            val itemsMethod = adapter::class.declaredMemberProperties.find { x -> x.name == "items" }
            assertNotNull(itemsMethod)
            itemsMethod.call(adapter)
        } as ArrayList<CharSequence>
    }
