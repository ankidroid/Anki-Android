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

package com.ichi2.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.util.AttributeSet;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.MetaDB;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;

@SuppressWarnings("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 see implementation at ResetLanguageDialogPreference
public class CustomDialogPreference extends android.preference.DialogPreference implements DialogInterface.OnClickListener {
    private final Context mContext;


    public CustomDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }


    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (this.getTitle().equals(mContext.getResources().getString(R.string.deck_conf_reset))) {
                // Deck Options :: Restore Defaults for Options Group
                Editor editor = AnkiDroidApp.getSharedPrefs(mContext).edit();
                editor.putBoolean("confReset", true);
                editor.commit();
            } else if (this.getTitle().equals(mContext.getResources().getString(R.string.dialog_positive_remove))) {
                // Deck Options :: Remove Options Group
                Editor editor = AnkiDroidApp.getSharedPrefs(mContext).edit();
                editor.putBoolean("confRemove", true);
                editor.commit();
            } else if (this.getTitle().equals(mContext.getResources().getString(R.string.deck_conf_set_subdecks))) {
                // Deck Options :: Set Options Group for all Sub-decks
                Editor editor = AnkiDroidApp.getSharedPrefs(mContext).edit();
                editor.putBoolean("confSetSubdecks", true);
                editor.commit();
            }
        }
    }

}
