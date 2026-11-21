// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>

package com.ichi2.anki

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import anki.collection.OpChangesAfterUndo
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toUndoOperationSentenceCase
import com.ichi2.utils.LanguageUtil

suspend fun tryUndo(): String {
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
        TR.undoActionUndone(
            changes.operation.toUndoOperationSentenceCase(
                LanguageUtil.getAppLocale(appContext.resources),
            ),
        )
    }
}

suspend fun tryRedo(): String {
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
        TR.undoActionRedone(
            changes.operation.toUndoOperationSentenceCase(
                LanguageUtil.getAppLocale(appContext.resources),
            ),
        )
    }
}

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun FragmentActivity.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val text = tryUndo()
        showSnackbar(text, duration)
    }
}

/** If there's an action pending in the review queue, undo it and show a snackbar */
suspend fun Fragment.undoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    requireActivity().undoAndShowSnackbar(duration)
}

suspend fun FragmentActivity.redoAndShowSnackbar(duration: Int = Snackbar.LENGTH_SHORT) {
    withProgress {
        val text = tryRedo()
        showSnackbar(text, duration)
    }
}
