/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs.utils

import android.net.Uri
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooperWithSleep

class FragmentTestActivity : AnkiActivity() {
    var lastUrlOpened: String? = null
        private set
    lateinit var lastShownDialogFragment: DialogFragment
        private set

    override fun openUrl(url: Uri) {
        lastUrlOpened = url.toString()
        super.openUrl(url)
    }

    override fun showDialogFragment(newFragment: DialogFragment) {
        super.showDialogFragment(newFragment)
        lastShownDialogFragment = newFragment
        // Note: I saw a potential solution for this sleeping on StackOverflow - can't find the code again.
        advanceRobolectricLooperWithSleep() // 6 of normal advance wasn't enough
        advanceRobolectricLooperWithSleep() // 1 sleep wasn't enough :/
    }
}
