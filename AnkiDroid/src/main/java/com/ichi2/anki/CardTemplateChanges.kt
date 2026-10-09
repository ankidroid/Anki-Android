// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CardTemplateNotetype.ChangeType
import com.ichi2.anki.CardTemplateNotetype.TemplateChange
import timber.log.Timber

/** Tracks unsaved template operations and translates editor positions to database ordinals. */
class CardTemplateChanges {
    var templateChanges = ArrayList<TemplateChange>()

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
        dumpChanges()
        Timber.d("getDeleteDbOrds()")

        // array containing the original / db-relative ordinals for all pending deletes plus the proposed one
        val deletedDbOrds = ArrayList<Int>(templateChanges.size)

        // For each entry in the changes list - and the proposed delete - scan for deletes to get original ordinal
        for (i in 0..templateChanges.size) {
            var ordinalAdjustment = 0

            // We need an initializer. Though proposed change is checked last, it's a reasonable default initializer.
            var currentChange = TemplateChange(ord, ChangeType.DELETE)
            if (i < templateChanges.size) {
                // Until we exhaust the pending change list we will use them
                currentChange = templateChanges[i]
            }

            // If the current pending change isn't a delete, it is unimportant here
            if (currentChange.type !== ChangeType.DELETE) {
                continue
            }

            // If it is a delete, scan previous deletes and shift as necessary for original ord
            for (j in 0 until i) {
                val previousChange = templateChanges[j]

                // Is previous change a delete? Lower ordinal than current change?
                if (previousChange.type === ChangeType.DELETE && previousChange.ordinal <= currentChange.ordinal) {
                    // If so, that is the case where things shift. It means our ordinals moved and original ord is higher
                    ordinalAdjustment++
                }
            }

            // We know how many times ordinals smaller than the current were deleted so we have the total adjustment
            // Save this pending delete at it's original / db-relative position
            deletedDbOrds.add(currentChange.ordinal + ordinalAdjustment)
        }
        val deletedDbOrdInts = IntArray(deletedDbOrds.size)
        for (i in deletedDbOrdInts.indices) {
            deletedDbOrdInts[i] = deletedDbOrds[i]
        }
        return deletedDbOrdInts
    }

    fun dumpChanges() {
        if (!BuildConfig.DEBUG) {
            return
        }
        val adjustedChanges = adjustedTemplateChanges
        for (i in templateChanges.indices) {
            val change = templateChanges[i]
            val adjustedChange = adjustedChanges[i]
            Timber.d("dumpChanges() Change %s is ord/type %s/%s", i, change.ordinal, change.type)
            Timber.d(
                "dumpChanges() During save change %s will be ord/type %s/%s",
                i,
                adjustedChange.ordinal,
                adjustedChange.type,
            )
        }
    }

    /**
     * Adjust the ordinals in our accrued change list so that any pending adds have the correct
     * ordinal after taking into account any pending deletes
     *
     * @return ArrayList<Object></Object>[2]> of [ordinal][ChangeType] entries
     */
    val adjustedTemplateChanges: ArrayList<TemplateChange>
        get() {
            val changes = templateChanges
            val adjustedChanges = ArrayList<TemplateChange>(changes.size)

            // In order to save the changes into the database, the ordinals in the changelist must correspond to the
            // ordinals in the database (for deletes) or the correct index in the changes array (for adds)
            // It is not possible to know what those will be until the user requests a save, so they are stored in the
            // change list as-is until the save time comes, then the adjustment is made all at once
            for (i in changes.indices) {
                val change = changes[i]
                val adjustedChange =
                    when (change.type) {
                        ChangeType.ADD -> {
                            val adjustedOrdinal = getAdjustedAddOrdinalAtChangeIndex(this, i)
                            Timber.d(
                                "getAdjustedTemplateChanges() change %s ordinal adjusted from %s to %s",
                                i,
                                change.ordinal,
                                adjustedOrdinal,
                            )
                            TemplateChange(adjustedOrdinal, ChangeType.ADD)
                        }
                        ChangeType.DELETE -> change
                    }
                adjustedChanges.add(adjustedChange)
            }
            return adjustedChanges
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
            var ordinalAdjustment = 0
            val change = noteType.templateChanges[changesIndex]
            val ordinalToInspect = change.ordinal
            for (i in noteType.templateChanges.size - 1 downTo changesIndex) {
                val oldChange = noteType.templateChanges[i]
                val currentOrdinal = change.ordinal
                when (oldChange.type) {
                    ChangeType.DELETE -> {
                        // Deleting an ordinal at or below us? Adjust our comparison basis...
                        if (currentOrdinal - ordinalAdjustment <= ordinalToInspect) {
                            ordinalAdjustment++
                            continue
                        }
                        Timber.d(
                            "getAdjustedAddOrdinalAtChangeIndex() contemplating delete at index %s, current ord adj %s",
                            i,
                            ordinalAdjustment,
                        )
                    }
                    ChangeType.ADD ->
                        if (changesIndex == i) {
                            // something we added this session
                            Timber.d(
                                "getAdjustedAddOrdinalAtChangeIndex() pending add found at at index %s, old ord/adjusted ord %s/%s",
                                i,
                                currentOrdinal,
                                currentOrdinal - ordinalAdjustment,
                            )
                            return currentOrdinal - ordinalAdjustment
                        }
                }
            }
            Timber.d(
                "getAdjustedAddOrdinalAtChangeIndex() determined changesIndex %s was not a pending add",
                changesIndex,
            )
            return -1
        }
    }
}
