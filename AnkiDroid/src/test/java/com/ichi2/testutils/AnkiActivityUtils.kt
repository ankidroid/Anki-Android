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

import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.utils.KotlinCleanup

object AnkiActivityUtils {
    @Suppress("unused")
    @KotlinCleanup("replace getDialogFragment with this")
    inline fun <reified T : DialogFragment> AnkiActivity.getDialogFragmentKt(): T? {
        return supportFragmentManager.findFragmentByTag(AnkiActivity.DIALOG_FRAGMENT_TAG) as T?
    }

    @JvmStatic
    fun getDialogFragment(activity: AnkiActivity): DialogFragment? {
        return activity.supportFragmentManager.findFragmentByTag(AnkiActivity.DIALOG_FRAGMENT_TAG) as DialogFragment?
    }
}
