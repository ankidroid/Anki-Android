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
import android.view.WindowManager.BadTokenException;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiActivity;

import timber.log.Timber;

public class StyledProgressDialog extends Dialog {

    private Context mContext;


    public StyledProgressDialog(Context context) {
        super(context);
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
    }


    private static boolean animationEnabled(Context context) {
        if (context instanceof AnkiActivity) {
            return ((AnkiActivity) context).animationEnabled();
        } else {
            return true;
        }
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
