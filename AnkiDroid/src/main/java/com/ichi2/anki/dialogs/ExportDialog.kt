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

import android.os.Bundle
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.BundleUtils.getNullableLong
import com.ichi2.utils.contentNullable

class ExportDialog(private val listener: ExportDialogListener) : AnalyticsDialogFragment() {
    interface ExportDialogListener {
        fun exportApkg(path: String?, did: Long?, includeSched: Boolean, includeMedia: Boolean)
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
    @JvmOverloads
    fun withArguments(dialogMessage: String, did: Long? = null): ExportDialog {
        var args = this.arguments
        if (args == null) {
            args = Bundle()
        }
        if (did != null) {
            args.putLong("did", did)
        }
        args.putString("dialogMessage", dialogMessage)
        this.arguments = args
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): MaterialDialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val did = getNullableLong(arguments, "did")
        val checked: Array<Int>
        if (did != null) {
            mIncludeSched = false
            checked = arrayOf()
        } else {
            mIncludeSched = true
            checked = arrayOf(INCLUDE_SCHED)
        }
        val items = arrayOf(
            res.getString(R.string.export_include_schedule),
            res.getString(R.string.export_include_media)
        )
        val builder = MaterialDialog.Builder(requireActivity())
            .title(R.string.export)
            .contentNullable(requireArguments().getString("dialogMessage"))
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .cancelable(true)
            .items(*items)
            .alwaysCallMultiChoiceCallback()
            .itemsCallbackMultiChoice(
                checked
            ) { _: MaterialDialog?, integers: Array<Int?>, _: Array<CharSequence?>? ->
                mIncludeMedia = false
                mIncludeSched = false
                for (integer in integers) {
                    when (integer) {
                        INCLUDE_SCHED -> mIncludeSched = true
                        INCLUDE_MEDIA -> mIncludeMedia = true
                    }
                }
                true
            }
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                listener.exportApkg(null, did, mIncludeSched, mIncludeMedia)
                dismissAllDialogFragments()
            }
            .onNegative { _: MaterialDialog?, _: DialogAction? -> dismissAllDialogFragments() }
        return builder.show()
    }

    fun dismissAllDialogFragments() {
        listener.dismissAllDialogFragments()
    }

    companion object {
        private const val INCLUDE_SCHED = 0
        private const val INCLUDE_MEDIA = 1
    }
}
