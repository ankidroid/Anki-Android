/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.preferences

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat

class ConfirmationPreferenceCompat : DialogPreference {
    private var mCancelHandler = Runnable {}
    private var mOkHandler = Runnable {}

    @Suppress("Unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
    }

    @Suppress("Unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    }

    @Suppress("Unused")
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
    }

    @Suppress("Unused")
    constructor(context: Context?) : super(context) {
    }

    @Suppress("Unused")
    fun setCancelHandler(cancelHandler: Runnable) {
        mCancelHandler = cancelHandler
    }

    fun setOkHandler(okHandler: Runnable) {
        mOkHandler = okHandler
    }

    class ConfirmationDialogFragmentCompat : PreferenceDialogFragmentCompat() {
        override fun getPreference(): ConfirmationPreferenceCompat {
            return super.getPreference() as ConfirmationPreferenceCompat
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                preference.mOkHandler.run()
            } else {
                preference.mCancelHandler.run()
            }
        }

        companion object {
            @JvmStatic
            fun newInstance(key: String): ConfirmationDialogFragmentCompat {
                val fragment = ConfirmationDialogFragmentCompat()
                val b = Bundle(1)
                b.putString(ARG_KEY, key)
                fragment.arguments = b
                return fragment
            }
        }
    }
}
