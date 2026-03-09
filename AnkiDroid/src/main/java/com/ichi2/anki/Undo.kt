/*
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesAfterUndo
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.undoneMessageToSentenceCase
import com.ichi2.anki.ui.internationalization.redoneMessageToSentenceCase

suspend fun tryUndo(context: Context): String {
    val changes =
        undoableOp {
            if (undoAvailable()) {
                undo()
            } else {
                OpChangesAfterUndo.getDefaultInstance()
            }
        }
    return if (changes.operation.isEmpty()) {
        TR.actionsNothingToUndo()
    } else {
        // Convert "{Action} undone" to sentence case (e.g., "Empty cards undone")
        val message = TR.undoActionUndone(changes.operation)
        undoneMessageToSentenceCase(context, changes.operation)
    }
}

suspend fun tryRedo(context: Context): String {
    val changes =
        undoableOp {
            if (redoAvailable()) {
                redo()
            } else {
                OpChangesAfterUndo.getDefaultInstance()
            }
        }
    return if (changes.operation.isEmpty()) {
        TR.actionsNothingToRedo()
    } else {
        // Convert "Redo {Action}" to sentence case (e.g., "Redo empty cards")
        redoneMessageToSentenceCase(context, changes.operation)
    }
}

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun FragmentActivity.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val text = tryUndo(this@undoAndShowSnackbar)
        showSnackbar(text, duration)
    }
}

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun Fragment.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    requireActivity().undoAndShowSnackbar(duration)
}

suspend fun FragmentActivity.redoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val text = tryRedo(this@redoAndShowSnackbar)
        showSnackbar(text, duration)
    }
}
