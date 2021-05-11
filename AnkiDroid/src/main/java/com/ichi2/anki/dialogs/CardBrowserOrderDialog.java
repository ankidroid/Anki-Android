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

package com.ichi2.anki.dialogs;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.R;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class CardBrowserOrderDialog extends AnalyticsDialogFragment {

    private static MaterialDialog.ListCallbackSingleChoice mOrderDialogListener;


    public static CardBrowserOrderDialog newInstance(int order, boolean isOrderAsc,
            MaterialDialog.ListCallbackSingleChoice orderDialogListener) {
        CardBrowserOrderDialog f = new CardBrowserOrderDialog();
        Bundle args = new Bundle();
        args.putInt("order", order);
        args.putBoolean("isOrderAsc", isOrderAsc);
        mOrderDialogListener = orderDialogListener;
        f.setArguments(args);
        return f;
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources res = getResources();
        String[] items = res.getStringArray(R.array.card_browser_order_labels);
        // Set sort order arrow
        for (int i = 0; i < items.length; ++i) {
            if (i != CardBrowser.CARD_ORDER_NONE && i == getArguments().getInt("order")) {
                if (getArguments().getBoolean("isOrderAsc")) {
                    items[i] = items[i] + " (\u25b2)";
                } else {
                    items[i] = items[i] + " (\u25bc)";
                }
            }
        }
        return new MaterialDialog.Builder(getActivity())
                .title(res.getString(R.string.card_browser_change_display_order_title))
                .content(res.getString(R.string.card_browser_change_display_order_reverse))
                .items(items)
                .itemsCallbackSingleChoice(getArguments().getInt("order"), mOrderDialogListener)
                .build();
    }

}
