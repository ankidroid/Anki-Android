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
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.activityViewModels
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.ReverseDirection
import com.ichi2.anki.model.SortType

/**
 * Allows a user to set the [SortType] and [ReverseDirection]
 */
class CardBrowserOrderDialog : AnalyticsDialogFragment() {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val items = resources.getStringArray(R.array.card_browser_order_labels)
        // Set sort order arrow
        for (i in items.indices) {
            if (i != CARD_ORDER_NONE && i == viewModel.order.cardBrowserLabelIndex) {
                if (viewModel.orderAsc) {
                    items[i] = items[i].toString() + " (\u25b2)"
                } else {
                    items[i] = items[i].toString() + " (\u25bc)"
                }
            }
        }

        return AlertDialog.Builder(ContextThemeWrapper(requireActivity(), R.style.AlertDialogStyle))
            .setTitle(R.string.card_browser_change_display_order_title)
            .setSingleChoiceItems(items, viewModel.order.cardBrowserLabelIndex, orderSingleChoiceDialogListener)
            .create()
    }

    companion object {
        private var orderSingleChoiceDialogListener: DialogInterface.OnClickListener? = null

        // SortType.NO_SORTING.cardBrowserLabelIndex
        private const val CARD_ORDER_NONE = 0

        fun newInstance(orderSingleChoiceDialogListener: DialogInterface.OnClickListener): CardBrowserOrderDialog {
            this.orderSingleChoiceDialogListener = orderSingleChoiceDialogListener
            return CardBrowserOrderDialog()
        }
    }
}
