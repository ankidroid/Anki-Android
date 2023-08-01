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
import anki.collection.opChanges
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.undoNew
import com.ichi2.libanki.undoableOp
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.RustCleanup

/** If there's an action pending in the review queue, undo it and show a pop-up. */
suspend fun FragmentActivity.undoAndShowPopup() {
    withProgress {
        try {
            val changes = undoableOp {
                if (!undoAvailable()) {
                    OpChangesAfterUndo.getDefaultInstance()
                } else {
                    undoNew()
                }
            }
            showSnackbar(TR.undoActionUndone(changes.operation))
        } catch (exc: BackendException) {
            @RustCleanup("Backend module should export this as a separate Exception")
            if (exc.localizedMessage == "UndoEmpty") {
                // backend undo queue empty; try legacy v2 undo
                withCol {
                    col.legacyV2ReviewUndo()
                    reset()
                }
                // synthesize a change so screens update
                undoableOp {
                    opChanges {
                        card = true
                        deck = true
                        note = true // may have been a leech
                        studyQueues = true
                        browserTable = true
                    }
                }
                showSnackbar(TR.undoActionUndone(TR.schedulingReview()))
            }
        }
    }
}
