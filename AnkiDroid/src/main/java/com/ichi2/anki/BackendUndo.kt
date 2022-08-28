/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                       *
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

package com.ichi2.anki

import android.annotation.SuppressLint
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.libanki.undoNew
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.BlocksSchemaUpgrade
import net.ankiweb.rsdroid.BackendException

@SuppressLint("DirectSnackbarMakeUsage")
suspend fun FragmentActivity.backendUndoAndShowPopup(): Boolean {
    return try {
        val changes = withProgress() {
            undoableOp {
                undoNew()
            }
        }
        val ans_button = findViewById(R.id.flip_card) as Button
        val snackbar = Snackbar.make(findViewById(android.R.id.content), TR.undoActionUndone(changes.operation), Snackbar.LENGTH_LONG)
        snackbar.setAnchorView(ans_button) // sets the anchorview as answerbutton
        snackbar.show()

        true
    } catch (exc: BackendException) {
        @BlocksSchemaUpgrade("Backend module should export this as a separate Exception")
        if (exc.localizedMessage == "UndoEmpty") {
            // backend undo queue empty
            false
        } else {
            throw exc
        }
    }
}
