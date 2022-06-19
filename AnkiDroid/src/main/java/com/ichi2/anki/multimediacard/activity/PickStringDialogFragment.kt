/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.activity

import android.R
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.ichi2.utils.KotlinCleanup
import java.util.*

/**
 * This dialog fragment support a choice from a list of strings.
 */
class PickStringDialogFragment : DialogFragment() {
    private var mPossibleChoices: ArrayList<String>? = null
    private var mListener: DialogInterface.OnClickListener? = null
    private var mTitle: String? = null

    @KotlinCleanup("requireActivity")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use the Builder class for convenient dialog construction
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(mTitle)
        val adapter = ArrayAdapter(
            requireActivity(),
            R.layout.simple_list_item_1,
            mPossibleChoices!!
        )
        builder.setAdapter(adapter, mListener)
        return builder.create()
    }

    fun setTitle(title: String?) {
        mTitle = title
    }

    fun setChoices(possibleClones: ArrayList<String>?) {
        mPossibleChoices = possibleClones
    }

    fun setOnclickListener(listener: DialogInterface.OnClickListener?) {
        mListener = listener
    }
}
