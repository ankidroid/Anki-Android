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
import android.view.View;
import android.view.WindowManager.BadTokenException;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.R;

import timber.log.Timber;

public class StyledProgressDialog extends Dialog {

    private Context mContext;


    public StyledProgressDialog(Context context) {
        super(context, R.style.StyledDialog);
        mContext = context;
    }


    @Override
    public void show() {
        try {
            setCanceledOnTouchOutside(false);
            super.show();
        } catch (BadTokenException e) {
            Timber.e(e, "Could not show dialog");
        }
    }


    public static MaterialDialog show(Context context, CharSequence title, CharSequence message) {
        return show(context, title, message, false, null);
    }


    public static MaterialDialog show(Context context, CharSequence title, CharSequence message,
            boolean cancelable) {
        return show(context, title, message, cancelable, null);
    }


    public static MaterialDialog show(Context context, CharSequence title, CharSequence message,
            boolean cancelable, DialogInterface.OnCancelListener cancelListener) {
        return new MaterialDialog.Builder(context)
                .title(title)
                .content(message)
                .progress(true, 0)
                .cancelable(cancelable)
                .cancelListener(cancelListener)
                .show();
        /*final StyledProgressDialog dialog = new StyledProgressDialog(context);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View layout = inflater.inflate(R.layout.styled_progress_dialog, null);
        dialog.addContentView(layout, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        // set title
        if (title != null && title.length() > 0) {
            ((TextView) layout.findViewById(R.id.alertTitle)).setText(title);
            // if (icon != 0) {
            // ((ImageView) layout.findViewById(R.id.icon)).setImageResource(icon);
            // } else {
            // layout.findViewById(R.id.icon).setVisibility(View.GONE);
            // }
        } else {
            layout.findViewById(R.id.topPanel).setVisibility(View.GONE);
            layout.findViewById(R.id.titleDivider).setVisibility(View.GONE);
        }

        // set the message
        if (message != null) {
            TextView tv = (TextView) layout.findViewById(R.id.message);
            tv.setText(message);
            // if (messageSize != 0) {
            // tv.setTextSize(messageSize * context.getResources().getDisplayMetrics().scaledDensity);
            // }
        }

        // set background
        try {
            Themes.setStyledProgressDialogDialogBackgrounds(layout);
        } catch (OutOfMemoryError e) {
            Timber.e(e, "StyledDialog - Dialog could not be created");
            Themes.showThemedToast(context, context.getResources().getString(R.string.error_insufficient_memory), false);
            return null;
        }

        dialog.setContentView(layout);
        dialog.setCancelable(cancelable);
        dialog.setOnCancelListener(cancelListener);
        if (animationEnabled(context)) {
            dialog.show();
        }
        return dialog;*/

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
        ((TextView) main.findViewById(R.id.message)).setText(message);
        ((View) main.findViewById(R.id.contentPanel)).setVisibility(View.VISIBLE);
    }


    public void setTitle(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.alertTitle)).setText(message);
    }


    public void setMessage(String message) {
        View main = super.getWindow().getDecorView();
        ((TextView) main.findViewById(R.id.message)).setText(message);
        ((View) main.findViewById(R.id.contentPanel)).setVisibility(View.VISIBLE);
    }


    public void setMax(int max) {
        // TODO
    }


    public void setProgress(int progress) {
        // TODO
    }


    public void setProgressStyle(int style) {
        // TODO
    }

}
