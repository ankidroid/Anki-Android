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

package com.ichi2.libanki

import anki.collection.OpChangesAfterUndo
import net.ankiweb.rsdroid.RustCleanup
import anki.collection.UndoStatus as UndoStatusProto

/**
 * If undo/redo available, a localized string describing the action will be set.
 */
data class UndoStatus(
    val undo: String?,
    val redo: String?,
    // not currently used
    val lastStep: Int
) {
    companion object {
        fun from(proto: UndoStatusProto): UndoStatus {
            return UndoStatus(
                undo = proto.undo.ifEmpty { null },
                redo = proto.redo.ifEmpty { null },
                lastStep = proto.lastStep
            )
        }
    }
}

/**
 * Undo the last backend operation.
 *
 * Should be called via collection.op(), which will notify
 * [ChangeManager.Subscriber] of the changes.
 *
 * Will throw if no undo operation is possible (due to legacy code
 * directly mutating the database).
 */
@RustCleanup("Once fully migrated, and v2 scheduler dropped, rename to undo()")
fun CollectionV16.undoNew(): OpChangesAfterUndo {
    val changes = backend.undo()
    // clear legacy undo log
    clearUndo()
    return changes
}

/** Redoes the previously-undone operation. See the docs for
[CollectionV16.undoOperation]
 */
fun CollectionV16.redo(): OpChangesAfterUndo {
    val changes = backend.redo()
    // clear legacy undo log
    clearUndo()
    return changes
}

/** See [UndoStatus] */
fun CollectionV16.undoStatus(): UndoStatus {
    return UndoStatus.from(backend.getUndoStatus())
}
