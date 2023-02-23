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

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.undoNew
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.BlocksSchemaUpgrade
import net.ankiweb.rsdroid.BackendException

/** @param anchorView The view to display the snackbar above */
suspend fun FragmentActivity.backendUndoAndShowPopup(anchorView: View? = null): Boolean {
    return try {
        val changes = withProgress() {
            undoableOp {
                undoNew()
            }
        }

        showSnackbar(TR.undoActionUndone(changes.operation)) {
            // A snackbar may obscure vital elements (e.g: the answer buttons on the Reviewer)
            // `anchorView` stops this
            anchorView?.let { setAnchorView(anchorView) }
        }

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
