// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesWithCount
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.UndoStatus
import com.ichi2.anki.libanki.redoAvailable
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import timber.log.Timber

/**
 * Limits undo/redo to this screen's changes until another caller changes shared history.
 * Access only inside [withCol], including when recording local operations.
 */
internal class ManageTagsUndoHistory {
    private var undoCount = 0
    private var redoCount = 0
    private var expectedUndoStatus: UndoStatus? = null

    /**
     * Allows undo/redo to reach history from before this Manage Tags screen opened.
     *
     * Keeping this `false` is the better UX: undo stops at the screen's opening boundary,
     * protecting work done before opening it. If another caller changes shared history,
     * the local counts become unreliable. We then allow earlier history so interleaved
     * API actions remain undoable, even though the opening boundary can no longer be enforced.
     *
     * TODO: Preserve the opening boundary even with interleaved API actions.
     */
    private var allowEarlierHistory = false

    fun undo(col: Collection): OpChangesAfterUndo {
        checkHistory(col)
        if (!col.undoAvailable() || (undoCount == 0 && !allowEarlierHistory)) {
            return OpChangesAfterUndo.getDefaultInstance()
        }
        return col.undo().also {
            if (!allowEarlierHistory) {
                undoCount--
                redoCount++
            }
            expectedUndoStatus = col.undoStatus()
        }
    }

    fun redo(col: Collection): OpChangesAfterUndo {
        checkHistory(col)
        if (!col.redoAvailable() || (redoCount == 0 && !allowEarlierHistory)) {
            return OpChangesAfterUndo.getDefaultInstance()
        }
        return col.redo().also {
            if (!allowEarlierHistory) {
                redoCount--
                undoCount++
            }
            expectedUndoStatus = col.undoStatus()
        }
    }

    /** Records actual undo entries, including cache-only deletions with no affected notes. */
    fun trackTagChange(
        col: Collection,
        block: Collection.() -> OpChangesWithCount,
    ): OpChangesWithCount {
        checkHistory(col)
        return col.block().also {
            if (it.changes.mtime && !allowEarlierHistory) {
                undoCount++
                redoCount = 0
            }
            // No-op tag operations can advance lastStep without creating an undo entry.
            expectedUndoStatus = col.undoStatus()
        }
    }

    /** [block] must not add an undo entry, as with saving expansion preferences. */
    fun <T> trackNonUndoableChange(
        col: Collection,
        block: Collection.() -> T,
    ): T {
        checkHistory(col)
        return col.block().also {
            // Expansion preferences change the backend counter without adding an undo step.
            expectedUndoStatus = col.undoStatus()
        }
    }

    /** Localized undo/redo labels, or null when the corresponding action is unavailable. */
    fun labels(col: Collection): UndoRedoLabels {
        checkHistory(col)
        return UndoRedoLabels(
            undoLabel = col.undoLabel().takeIf { allowEarlierHistory || undoCount > 0 },
            redoLabel = col.redoLabel().takeIf { allowEarlierHistory || redoCount > 0 },
        )
    }

    private fun checkHistory(col: Collection) {
        val current = col.undoStatus()
        if (!allowEarlierHistory && expectedUndoStatus != null && current != expectedUndoStatus) {
            allowEarlierHistory = true
            Timber.w(
                "Undo history changed outside Manage Tags; allowing undo before this screen opened (expected=%s, current=%s)",
                expectedUndoStatus,
                current,
            )
        }
        if (current.undo == null) undoCount = 0
        if (current.redo == null) redoCount = 0
        expectedUndoStatus = current
    }
}

/** Localized action labels; null means the corresponding action is unavailable. */
internal data class UndoRedoLabels(
    val undoLabel: String?,
    val redoLabel: String?,
)
