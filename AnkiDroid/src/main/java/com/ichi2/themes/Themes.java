/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.compat.CompatHelper;

import timber.log.Timber;

public class Themes {

    public final static int THEME_WHITE = 3;

    public final static int CALLER_STUDYOPTIONS = 1;
    public final static int CALLER_REVIEWER = 4;
    public final static int CALLER_DECKPICKER = 7;
    public final static int CALLER_CARDBROWSER = 8;
    public final static int CALLER_CARDEDITOR_INTENTDIALOG = 9;
    public final static int CALLER_CARD_EDITOR = 10;

    public final static int ALPHA_ICON_ENABLED_LIGHT = 255; // 100%
    public final static int ALPHA_ICON_DISABLED_LIGHT = 76; // 31%
    public final static int ALPHA_ICON_ENABLED_DARK = 138; // 54%


    private static int mCurrentTheme = -1;
    private static int mReviewerBackground = 0;
    private static int mDialogBackgroundColor = 0;



    public static void setContentStyle(View view, int caller) {
        switch (caller) {
            case CALLER_REVIEWER:
                ((View) view.findViewById(R.id.main_layout)).setBackgroundResource(mReviewerBackground);
                switch (mCurrentTheme) {
                    case THEME_WHITE:
                        ((View) view.findViewById(R.id.flashcard_frame)).setBackgroundResource(AnkiDroidApp
                                .getSharedPrefs(view.getContext()).getBoolean("invertedColors", false) ? R.color.black
                                : R.color.white);

                        setMargins(view.findViewById(R.id.main_layout), LayoutParams.FILL_PARENT,
                                LayoutParams.FILL_PARENT, 4f, 0, 4f, 4f);
                        break;
                }
                break;
        }
    }


    public static String getReviewerFontName() {
        switch (mCurrentTheme) {
            case THEME_WHITE:
                return "OpenSans";
            default:
                return null;
        }
    }


    public static void setMargins(View view, int width, int height, float dipLeft, float dipTop, float dipRight,
            float dipBottom) {
        View parent = (View) view.getParent();
        Class<?> c = view.getParent().getClass();
        float factor = view.getContext().getResources().getDisplayMetrics().density;
        if (c == LinearLayout.class) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            view.setLayoutParams(llp);
        } else if (c == FrameLayout.class) {
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            llp.gravity = Gravity.CENTER_HORIZONTAL;
            view.setLayoutParams(llp);
        } else if (c == RelativeLayout.class) {
            RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(width, height);
            llp.setMargins((int) (dipLeft * factor), (int) (dipTop * factor), (int) (dipRight * factor),
                    (int) (dipBottom * factor));
            view.setLayoutParams(llp);
        }
    }


    public static int getTheme() {
        return mCurrentTheme;
    }


    public static void showThemedToast(Context context, String text, boolean shortLength) {
        Toast.makeText(context, text, shortLength ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }


    public static MaterialDialog htmlOkDialog(Context context, String title, String text) {
        return htmlOkDialog(context, title, text, null, null);
    }


    public static MaterialDialog htmlOkDialog(Context context, String title, String text, MaterialDialog.ButtonCallback okListener,
            OnCancelListener cancelListener) {
        return htmlOkDialog(context, title, text, okListener, cancelListener, false);
    }


    public static MaterialDialog htmlOkDialog(Context context, String title, String text, MaterialDialog.ButtonCallback okListener,
            OnCancelListener cancelListener, boolean includeBody) {
        WebView view = new WebView(context);
        view.setBackgroundColor(context.getResources().getColor(mDialogBackgroundColor));
        if (includeBody) {
            text = "<html><body text=\"#FFFFFF\" link=\"#E37068\" alink=\"#E37068\" vlink=\"#E37068\">" + text
                    + "</body></html>";
        }
        view.loadDataWithBaseURL("", text, "text/html", "UTF-8", "");
        return new MaterialDialog.Builder(context)
                .title(title)
                .cancelable(true)
                .customView(view, true)
                .positiveText(context.getResources().getString(R.string.dialog_ok))
                .callback(okListener)
                .cancelListener(cancelListener)
                .build();
    }



    public static int getNightModeCardBackground(Context context) {
        switch (mCurrentTheme) {
            case THEME_WHITE:
            default:
                return context.getResources().getColor(R.color.black);
        }
    }


    public static int[] setNightMode(Context context, View view, boolean nightMode) {
        Resources res = context.getResources();
        View flipCard = view.findViewById(R.id.flashcard_layout_flip);
        View ease1 = view.findViewById(R.id.flashcard_layout_ease1);
        View ease2 = view.findViewById(R.id.flashcard_layout_ease2);
        View ease3 = view.findViewById(R.id.flashcard_layout_ease3);
        View ease4 = view.findViewById(R.id.flashcard_layout_ease4);
        //View border = view.findViewById(R.id.flashcard_border);
        View mAnswerField = view.findViewById(R.id.answer_field);
        final Drawable[] defaultButtons = new Drawable[] { flipCard.getBackground(), ease1.getBackground(),
                ease2.getBackground(), ease3.getBackground(), ease4.getBackground() };

        int foregroundColor;
        int nextTimeRecommendedColor;

        if (nightMode) {
            /*flipCard.setBackgroundResource(mNightModeButton);
            ease1.setBackgroundResource(mNightModeButton);
            ease2.setBackgroundResource(mNightModeButton);
            ease3.setBackgroundResource(mNightModeButton);
            ease4.setBackgroundResource(mNightModeButton);
            mAnswerField.setBackgroundResource(mNightModeButton);*/

            foregroundColor = Color.WHITE;
            nextTimeRecommendedColor = res.getColor(R.color.next_time_recommended_color_inv);

            switch (mCurrentTheme) {
                case THEME_WHITE:
                    //border.setBackgroundResource(R.drawable.white_bg_webview_night);
                    view.setBackgroundColor(res.getColor(R.color.white_background_night));
                    ((View) view.getParent()).setBackgroundColor(res.getColor(R.color.white_background_night));
                    break;
                default:
                    view.setBackgroundColor(res.getColor(R.color.black));
                    break;
            }
        } else {
            foregroundColor = Color.BLACK;
            nextTimeRecommendedColor = res.getColor(R.color.next_time_recommended_color);
            flipCard.setBackgroundDrawable(defaultButtons[0]);
            ease1.setBackgroundDrawable(defaultButtons[1]);
            ease2.setBackgroundDrawable(defaultButtons[2]);
            ease3.setBackgroundDrawable(defaultButtons[3]);
            ease4.setBackgroundDrawable(defaultButtons[4]);
        }

        return new int[] { foregroundColor, nextTimeRecommendedColor };
    }

}
