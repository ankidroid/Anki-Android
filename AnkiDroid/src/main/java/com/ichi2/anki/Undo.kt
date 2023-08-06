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

import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesAfterUndo
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.undo
import com.ichi2.libanki.undoableOp

/** If there's an action pending in the review queue, undo it and show a pop-up. */
suspend fun FragmentActivity.undoAndShowPopup() {
    withProgress {
        val changes = undoableOp {
            if (!undoAvailable()) {
                OpChangesAfterUndo.getDefaultInstance()
            } else {
                undo()
            }
        }
        showSnackbar(TR.undoActionUndone(changes.operation), Snackbar.LENGTH_SHORT)
    }
}
