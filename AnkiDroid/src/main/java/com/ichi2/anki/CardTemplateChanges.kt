// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CardTemplateNotetype.ChangeType
import com.ichi2.anki.CardTemplateNotetype.TemplateChange
import timber.log.Timber

/** Tracks unsaved template operations and translates editor positions to database ordinals. */
class CardTemplateChanges {
    var templateChanges = ArrayList<TemplateChange>()

    /** Database ordinal for a UI position, or null for a template added in this session. */
    fun getOriginalOrdinal(ordinal: Int): Int? = originalOrdinalBefore(ordinal, templateChanges.size)

    private fun originalOrdinalBefore(
        ordinal: Int,
        endIndex: Int,
    ): Int? {
        var position = ordinal
        for (i in endIndex - 1 downTo 0) {
            val change = templateChanges[i]
            when (change.type) {
                ChangeType.ADD -> {
                    if (position == change.ordinal) return null
                    if (position > change.ordinal) position--
                }
                ChangeType.DELETE -> if (position >= change.ordinal) position++
            }
        }
        return position
    }

    /**
     * Template deletes shift card ordinals in the database. To operate without saving, we must keep track to apply in order.
     * In addition, we don't want to persist a template add just to delete it later, so we combine those if they happen
     */
    fun addTemplateChange(
        type: ChangeType,
        ordinal: Int,
    ) {
        Timber.d("addTemplateChange() type %s for ordinal %s", type, ordinal)
        val templateChanges = templateChanges
        val change = TemplateChange(ordinal, type)

        // If we are deleting something we added but have not saved, edit it out of the change list
        if (type == ChangeType.DELETE) {
            var ordinalAdjustment = 0
            for (i in templateChanges.indices.reversed()) {
                val oldChange = templateChanges[i]
                when (oldChange.type) {
                    ChangeType.DELETE ->
                        if (oldChange.ordinal - ordinalAdjustment <= ordinal) {
                            // Deleting an ordinal at or below us? Adjust our comparison basis...
                            ordinalAdjustment++
                            continue
                        }
                    ChangeType.ADD ->
                        if (ordinal == oldChange.ordinal - ordinalAdjustment) {
                            // Deleting something we added this session? Edit it out via compaction
                            compactTemplateChanges(oldChange.ordinal)
                            return
                        }
                }
            }
        }
        Timber.d("addTemplateChange() added ord/type: %s/%s", change.ordinal, change.type)
        templateChanges.add(change)
        dumpChanges()
    }

    /**
     * Return an int[] containing the collection-relative ordinals of all the currently pending deletes,
     * including the ordinal passed in, as opposed to the changelist-relative ordinals
     *
     * @param ord int UI-relative ordinal to check database for delete safety along with existing deletes
     * @return int[] of all ordinals currently in the database, pending delete
     */
    fun getDeleteDbOrds(ord: Int): IntArray {
        val deleted =
            templateChanges.mapIndexedNotNull { index, change ->
                if (change.type == ChangeType.DELETE) originalOrdinalBefore(change.ordinal, index) else null
            }
        return (deleted + listOfNotNull(getOriginalOrdinal(ord))).toIntArray()
    }

    fun dumpChanges() {
        if (!BuildConfig.DEBUG) {
            return
        }
        for (i in templateChanges.indices) {
            val change = templateChanges[i]
            Timber.d("dumpChanges() Change %s is ord/type %s/%s", i, change.ordinal, change.type)
        }
    }

    /**
     * Scan the sequence of template add/deletes, looking for the given ordinal.
     * When found, purge that ordinal and shift future changes down if they had ordinals higher than the one purged
     */
    private fun compactTemplateChanges(addedOrdinalToDelete: Int) {
        Timber.d(
            "compactTemplateChanges() merge/purge add/delete ordinal added as %s",
            addedOrdinalToDelete,
        )
        var postChange = false
        var ordinalAdjustment = 0
        var i = 0
        while (i < templateChanges.size) {
            val change = templateChanges[i]
            var ordinal = change.ordinal
            val changeType = change.type
            Timber.d("compactTemplateChanges() examining change entry %s / %s", ordinal, changeType)

            // Only make adjustments after the ordinal we want to delete was added
            if (!postChange) {
                if (ordinal == addedOrdinalToDelete && changeType == ChangeType.ADD) {
                    Timber.d("compactTemplateChanges() found our entry at index %s", i)
                    // Remove this entry to start compaction, then fix up the loop counter since we altered size
                    postChange = true
                    templateChanges.removeAt(i)
                    i--
                }
                i++
                continue
            }

            // We compact all deletes with higher ordinals, so any delete is below us: shift our comparison basis
            if (changeType == ChangeType.DELETE) {
                ordinalAdjustment++
                Timber.d(
                    "compactTemplateChanges() delete affecting purged template, shifting basis, adj: %s",
                    ordinalAdjustment,
                )
            }

            // If following ordinals were higher, we move them as part of compaction
            if (ordinal + ordinalAdjustment > addedOrdinalToDelete) {
                Timber.d("compactTemplateChanges() shifting later/higher ordinal down")
                change.ordinal = --ordinal
            }
            i++
        }
    }

    companion object {
        /**
         * Check if the given ordinal from the current UI state (which includes all pending changes) is a pending add
         *
         * @param ord int representing an ordinal in the note type, that might be an unsaved addition
         * @return boolean true if it is a pending addition from this editing session
         */
        fun isOrdinalPendingAdd(
            noteType: CardTemplateChanges,
            ord: Int,
        ): Boolean {
            for (i in noteType.templateChanges.indices) {
                // commented out to make the code compile, why is this unused?
                // val change = noteType.templateChanges[i]
                val adjustedOrdinal = getAdjustedAddOrdinalAtChangeIndex(noteType, i)
                if (adjustedOrdinal == ord) {
                    Timber.d(
                        "isOrdinalPendingAdd() found ord %s was pending add (would adjust to %s)",
                        ord,
                        adjustedOrdinal,
                    )
                    return true
                }
            }
            Timber.d("isOrdinalPendingAdd() ord %s is not a pending add", ord)
            return false
        }

        /**
         * Check if the change at the given index in the changes array is an addition from this editing session
         * (and thus is not in the database yet, and possibly needing ordinal adjustment from subsequent deletes)
         * @param changesIndex the index of the template in the changes array
         * @return either ordinal adjusted by any pending deletes if it is a pending add, or -1 if the ordinal is not an add
         */
        fun getAdjustedAddOrdinalAtChangeIndex(
            noteType: CardTemplateChanges,
            changesIndex: Int,
        ): Int {
            if (changesIndex >= noteType.templateChanges.size) {
                return -1
            }
            val change = noteType.templateChanges[changesIndex]
            if (change.type != ChangeType.ADD) return -1
            var position = change.ordinal
            for (later in noteType.templateChanges.drop(changesIndex + 1)) {
                when (later.type) {
                    ChangeType.ADD -> if (position >= later.ordinal) position++
                    ChangeType.DELETE -> {
                        if (position == later.ordinal) return -1
                        if (position > later.ordinal) position--
                    }
                }
            }
            return position
        }
    }
}
