/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Alexander Grüneberg <alexander.grueneberg@googlemail.com>         *
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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.ichi2.anki.CardBrowser;
import com.ichi2.anki.R;
import com.ichi2.themes.StyledDialog;

public class CardBrowserDisplayOrderDialog extends DialogFragment {

    public static CardBrowserDisplayOrderDialog newInstance(int order, boolean orderAsc) {
        CardBrowserDisplayOrderDialog dialogFragment = new CardBrowserDisplayOrderDialog();
        Bundle args = new Bundle();
        args.putInt("order", order);
        args.putBoolean("orderAsc", orderAsc);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Resources res = getActivity().getResources();
        int order = getArguments().getInt("order");
        boolean orderAsc = getArguments().getBoolean("orderAsc");
        String[] orderLabels = res.getStringArray(R.array.card_browser_order_labels);
        for (int i = 0; i < orderLabels.length; i++) {
            if (i != CardBrowser.CARD_ORDER_NONE && i == order) {
                if (orderAsc) {
                    orderLabels[i] = orderLabels[i] + " (\u25b2)";
                } else {
                    orderLabels[i] = orderLabels[i] + " (\u25bc)";
                }
            }
        }
        return new StyledDialog.Builder(getActivity())
                .setTitle(res.getString(R.string.card_browser_change_display_order_title))
                .setMessage(res.getString(R.string.card_browser_change_display_order_reverse))
                .setSingleChoiceItems(orderLabels, order,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ((CardBrowser) getActivity()).changeDisplayOrder(which);
                            }
                        }).create();
    }
}
