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
package com.ichi2.anki.dialogs

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.ui.GesturePicker

/** Helper functions for a Dialog which wraps a [com.ichi2.ui.GesturePicker]  */
class GestureSelectionDialogBuilder(context: Context) : MaterialDialog.Builder(context) {
    private val mGesturePicker: GesturePicker = GesturePicker(context)

    /** Supplies a callback which is called each time the user gesture selection changes
     *
     * This is **not** when the gesture is submitted */
    fun onGestureChanged(listener: GestureListener): GestureSelectionDialogBuilder {
        mGesturePicker.setGestureChangedListener(listener)
        return this
    }

    /** Supplies a callback which is called when the user presses "OK" with a valid gesture */
    fun onGestureSubmitted(listener: GestureListener): GestureSelectionDialogBuilder {
        onPositive { _, _ ->
            val gesture = mGesturePicker.getGesture() ?: return@onPositive
            listener.onGesture(gesture)
        }
        return this
    }

    init {
        positiveText(R.string.dialog_ok)
        title(R.string.binding_add_gesture)
        customView(mGesturePicker, false)
    }
}
