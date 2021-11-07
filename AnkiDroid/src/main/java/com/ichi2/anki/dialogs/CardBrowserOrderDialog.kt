/****************************************************************************************
 * Copyright (c) 2015 Enno Hermann <enno.hermann@gmail.com>                             *
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

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallbackSingleChoice
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class CardBrowserOrderDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val items = res.getStringArray(R.array.card_browser_order_labels)
        // Set sort order arrow
        for (i in items.indices) {
            if (i != CardBrowser.CARD_ORDER_NONE && i == requireArguments().getInt("order")) {
                if (requireArguments().getBoolean("isOrderAsc")) {
                    items[i] = items[i].toString() + " (\u25b2)"
                } else {
                    items[i] = items[i].toString() + " (\u25bc)"
                }
            }
        }
        return MaterialDialog.Builder(requireActivity())
            .title(res.getString(R.string.card_browser_change_display_order_title))
            .content(res.getString(R.string.card_browser_change_display_order_reverse))
            .items(*items)
            .itemsCallbackSingleChoice(requireArguments().getInt("order"), mOrderDialogListener!!)
            .build()
    }

    companion object {
        private var mOrderDialogListener: ListCallbackSingleChoice? = null
        @JvmStatic
        fun newInstance(
            order: Int,
            isOrderAsc: Boolean,
            orderDialogListener: ListCallbackSingleChoice?
        ): CardBrowserOrderDialog {
            val f = CardBrowserOrderDialog()
            val args = Bundle()
            args.putInt("order", order)
            args.putBoolean("isOrderAsc", isOrderAsc)
            mOrderDialogListener = orderDialogListener
            f.arguments = args
            return f
        }
    }
}
