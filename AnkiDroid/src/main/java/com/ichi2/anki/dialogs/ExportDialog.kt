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
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.getNullableLong
import com.ichi2.utils.contentNullable

class ExportDialog(private val listener: ExportDialogListener) : AnalyticsDialogFragment() {
    interface ExportDialogListener {
        fun exportColAsApkgOrColpkg(path: String?, includeSched: Boolean, includeMedia: Boolean)
        fun exportDeckAsApkg(path: String?, did: DeckId, includeSched: Boolean, includeMedia: Boolean)
        fun exportSelectedAsApkg(path: String?, limit: ExportLimit, includeSched: Boolean, includeMedia: Boolean)
        fun dismissAllDialogFragments()
    }

    private var mIncludeSched = false
    private var mIncludeMedia = false

    /**
     * Creates a new instance of ExportDialog to export a deck of cards
     *
     * @param did A long which specifies the deck to be exported,
     *            if did is null then the whole collection of decks will be exported
     * @param dialogMessage A string which can be used to show a custom message or specify import path
     */
    fun withArguments(dialogMessage: String, did: DeckId? = null): ExportDialog {
        val args = this.arguments ?: Bundle()
        if (did != null) {
            args.putLong("did", did)
        }
        args.putString("dialogMessage", dialogMessage)
        this.arguments = args
        return this
    }

    /**
     * @param ids A list of longs which specifies the card/note ids to be exported
     * @param isCardList A boolean which specifies whether the list of ids is a list of card ids or note ids
     * @param dialogMessage A string which can be used to show a custom message or specify import path
     */
    fun withArguments(dialogMessage: String, ids: List<Long>, isCardList: Boolean): ExportDialog {
        val args = this.arguments ?: Bundle()
        args.putString("dialogMessage", dialogMessage)
        if (isCardList) {
            args.putLongArray("cardIds", ids.toLongArray())
        } else {
            args.putLongArray("noteIds", ids.toLongArray())
        }
        this.arguments = args
        return this
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val did = getNullableLong(arguments, "did")
        val cardIds = arguments?.getLongArray("cardIds")?.toList()
        val noteIds = arguments?.getLongArray("noteIds")?.toList()
        val checked: Array<Int>
        @NeedsTest("Test that correct options are checked given different arguments")
        if (did != null || cardIds == null || noteIds == null) {
            mIncludeSched = false
            checked = arrayOf()
        } else {
            mIncludeSched = true
            checked = arrayOf(INCLUDE_SCHED)
        }
        val items = listOf(
            res.getString(R.string.export_include_schedule),
            res.getString(R.string.export_include_media)
        )
        return MaterialDialog(requireActivity()).show {
            title(R.string.export)
            contentNullable(requireArguments().getString("dialogMessage"))
            positiveButton(android.R.string.ok) {
                if (did != null) {
                    listener.exportDeckAsApkg(null, did, mIncludeSched, mIncludeMedia)
                } else if (cardIds != null || noteIds != null) {
                    val limit = exportLimit {
                        if (cardIds != null) {
                            this.cardIds = this.cardIds.toBuilder().addAllCids(cardIds).build()
                        } else {
                            this.noteIds = this.noteIds.toBuilder().addAllNoteIds(noteIds).build()
                        }
                    }
                    listener.exportSelectedAsApkg(null, limit, mIncludeSched, mIncludeMedia)
                } else {
                    listener.exportColAsApkgOrColpkg(null, mIncludeSched, mIncludeMedia)
                }
                dismissAllDialogFragments()
            }
            negativeButton(android.R.string.cancel) {
                dismissAllDialogFragments()
            }
            cancelable(true)
            listItemsMultiChoice(
                items = items,
                initialSelection = checked.toIntArray(),
                allowEmptySelection = true,
                waitForPositiveButton = false
            ) { _: MaterialDialog, ints: IntArray, _: List<CharSequence> ->
                mIncludeMedia = ints.contains(INCLUDE_MEDIA)
                mIncludeSched = ints.contains(INCLUDE_SCHED)
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
