/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import anki.cards.cardIds
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import anki.notes.noteIds
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.ExportDialogParams.Companion.toExportDialogParams
import com.ichi2.anki.export.ExportType
import com.ichi2.anki.export.ExportType.*
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.getNullableLong
import com.ichi2.utils.contentNullable

class ExportDialog(private val listener: ExportDialogListener) : AnalyticsDialogFragment() {
    interface ExportDialogListener {
        fun exportColAsApkgOrColpkg(
            path: String?,
            includeSched: Boolean,
            includeMedia: Boolean,
        )

        fun exportDeckAsApkg(
            path: String?,
            did: DeckId,
            includeSched: Boolean,
            includeMedia: Boolean,
        )

        fun exportSelectedAsApkg(
            path: String?,
            limit: ExportLimit,
            includeSched: Boolean,
            includeMedia: Boolean,
        )

        fun dismissAllDialogFragments()
    }

    private var includeSched = false
    private var includeMedia = false

    fun withArguments(data: ExportDialogParams): ExportDialog {
        this.arguments = data.appendToBundle(this.arguments ?: Bundle())
        return this
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val exportData = requireArguments().toExportDialogParams()
        includeSched = exportData.includeScheduling
        includeMedia = exportData.includeMedia

        val initialSelection = mutableListOf<Int>()
        if (includeSched) initialSelection.add(INCLUDE_SCHED)
        if (includeMedia) initialSelection.add(INCLUDE_MEDIA)

        return MaterialDialog(requireActivity()).show {
            title(R.string.export)
            contentNullable(exportData.message)
            positiveButton(android.R.string.ok) {
                when (val exportType = exportData.exportType) {
                    is ExportDeck -> listener.exportDeckAsApkg(null, exportType.deckId, includeSched, includeMedia)
                    is ExportCollection -> listener.exportColAsApkgOrColpkg(null, includeSched, includeMedia)
                    is ExportNotes -> {
                        val limit = exportLimit { this.noteIds = noteIds { this.noteIds.addAll(exportType.nodeIds) } }
                        listener.exportSelectedAsApkg(null, limit, includeSched, includeMedia)
                    }
                    is ExportCards -> {
                        val limit = exportLimit { this.cardIds = cardIds { cids.addAll(exportType.cardIds) } }
                        listener.exportSelectedAsApkg(null, limit, includeSched, includeMedia)
                    }
                }
                dismissAllDialogFragments()
            }
            negativeButton(android.R.string.cancel) {
                dismissAllDialogFragments()
            }
            cancelable(true)
            listItemsMultiChoice(
                items =
                    listOf(
                        resources.getString(R.string.export_include_schedule),
                        resources.getString(R.string.export_include_media),
                    ),
                initialSelection = initialSelection.toIntArray(),
                allowEmptySelection = true,
                waitForPositiveButton = false,
            ) { _: MaterialDialog, ints: IntArray, _: List<CharSequence> ->
                includeMedia = ints.contains(INCLUDE_MEDIA)
                includeSched = ints.contains(INCLUDE_SCHED)
            }
        }
    }

    fun dismissAllDialogFragments() {
        listener.dismissAllDialogFragments()
    }

    companion object {
        private const val INCLUDE_SCHED = 0
        private const val INCLUDE_MEDIA = 1
    }
}

/**
 * @param message A dialog to display to the user when exporting
 */
class ExportDialogParams(val message: String, val exportType: ExportType, includeMedia: Boolean? = null) {
    val includeScheduling: Boolean =
        when (this.exportType) {
            is ExportNotes -> false
            is ExportCards -> false
            is ExportDeck -> false
            is ExportCollection -> true
        }
    val includeMedia = includeMedia ?: false

    fun appendToBundle(bundle: Bundle): Bundle {
        bundle.putString(MESSAGE, this.message)
        bundle.putBoolean(INCLUDE_MEDIA, this.includeMedia)

        when (this.exportType) {
            is ExportNotes -> bundle.putLongArray(NOTE_IDS, this.exportType.nodeIds.toLongArray())
            is ExportCards -> bundle.putLongArray(CARD_IDS, this.exportType.cardIds.toLongArray())
            is ExportDeck -> bundle.putLong(DECK_ID, this.exportType.deckId)
            is ExportCollection -> {}
        }
        return bundle
    }

    companion object {
        private const val MESSAGE = "dialogMessage"
        private const val DECK_ID = "did"
        private const val CARD_IDS = "cardIds"
        private const val NOTE_IDS = "noteIds"
        private const val INCLUDE_MEDIA = "includeMedia"

        private fun Bundle.toExportType(): ExportType {
            val did = getNullableLong(DECK_ID)
            val cardIds = getLongArray(CARD_IDS)
            val noteIds = getLongArray(NOTE_IDS)

            if (did != null) {
                return ExportDeck(did)
            }
            if (cardIds != null) {
                return ExportCards(cardIds.toList())
            }
            if (noteIds != null) {
                return ExportNotes(noteIds.toList())
            }
            return ExportCollection
        }

        fun Bundle.toExportDialogParams(): ExportDialogParams =
            ExportDialogParams(
                message = getString(MESSAGE)!!,
                exportType = this.toExportType(),
                includeMedia = getBoolean(INCLUDE_MEDIA),
            )
    }
}
