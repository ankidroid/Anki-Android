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

package com.ichi2.preferences

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import timber.log.Timber

class ResetLanguageDialogPreference : DialogPreference {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    class ResetLanguageDialogFragmentCompat : PreferenceDialogFragmentCompat() {

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) {
                return
            }
            Timber.i("Resetting languages")
            if (MetaDB.resetLanguages(context)) {
                UIUtils.showThemedToast(context, AnkiDroidApp.getAppResources().getString(R.string.reset_confirmation), true)
            }
        }

        companion object {
            @JvmStatic
            fun newInstance(key: String?): ResetLanguageDialogFragmentCompat {
                val fragment = ResetLanguageDialogFragmentCompat()
                val b = Bundle(1)
                b.putString(ARG_KEY, key)
                fragment.arguments = b
                return fragment
            }
        }
    }
}
