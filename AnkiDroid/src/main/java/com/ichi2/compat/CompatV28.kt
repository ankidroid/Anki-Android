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
package com.ichi2.compat

import android.annotation.TargetApi
import android.view.View
import androidx.annotation.IdRes
import com.ichi2.anki.AnkiActivity

@TargetApi(28)
open class CompatV28 : CompatV26(), Compat {
    override fun <T : View> requireViewById(activity: AnkiActivity, @IdRes id: Int): T = activity.requireViewById(id)
    override fun <T : View> requireViewById(view: View, @IdRes id: Int): T = view.requireViewById(id)
}
