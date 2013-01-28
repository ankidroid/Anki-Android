/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * based on custom Dialog windows by antoine vianey                                     *
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

package com.ichi2.themes;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.widget.TextView;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;

public class StyledOpenCollectionDialog extends Dialog {

	private View mMainLayout;
	
    public StyledOpenCollectionDialog(Context context) {
        super(context, R.style.StyledDialog);
    }


    @Override
    public void show() {
        try {
            setCanceledOnTouchOutside(false);
            super.show();
        } catch (BadTokenException e) {
            Log.e(AnkiDroidApp.TAG, "Could not show dialog: " + e);
        }
    }


    @Override
    public void dismiss() {
        try {
            super.dismiss();
        } catch (BadTokenException e) {
            Log.e(AnkiDroidApp.TAG, "Could not dismiss dialog: " + e);
        }
    }


    public static StyledOpenCollectionDialog show(Context context, CharSequence message, DialogInterface.OnCancelListener cancelListener) {
    	return show(context, message, cancelListener, null);
    }
    public static StyledOpenCollectionDialog show(Context context, CharSequence message, DialogInterface.OnCancelListener cancelListener, View.OnClickListener textClickListener) {
        final StyledOpenCollectionDialog dialog = new StyledOpenCollectionDialog(context);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        dialog.mMainLayout = inflater.inflate(R.layout.styled_open_collection_dialog, null);
        dialog.addContentView(dialog.mMainLayout, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        if (message != null) {
            TextView tv = (TextView) dialog.mMainLayout.findViewById(R.id.deckpicker_loading_layer_statusline);
            tv.setText(message);
            if (textClickListener != null) {
            	tv.setOnClickListener(textClickListener);
            }
        }

        dialog.setContentView(dialog.mMainLayout);
        dialog.setOnCancelListener(cancelListener);
        dialog.getWindow().getAttributes().windowAnimations = R.style.Animation_Translucent;
        if (animationEnabled(context)) {
            dialog.show();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.FILL_PARENT;
            lp.height = WindowManager.LayoutParams.FILL_PARENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        }
        return dialog;

    }

    private static boolean animationEnabled(Context context) {
        if (context instanceof AnkiActivity) {
            return ((AnkiActivity) context).animationEnabled();
        } else {
            return true;
        }
    }

    public void setMessage(CharSequence message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.deckpicker_loading_layer_statusline)).setText(message);
    }


    public void setMessage(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.deckpicker_loading_layer_statusline)).setText(message);
    }
}
