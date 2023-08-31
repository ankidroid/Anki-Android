/****************************************************************************************
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

package com.ichi2.preferences

import android.content.Context
import android.content.DialogInterface
import android.util.AttributeSet
import androidx.core.content.edit
import com.ichi2.anki.R
import com.ichi2.anki.preferences.sharedPrefs

// TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 see implementation at ResetLanguageDialogPreference
@Suppress("Deprecation", "Unused")
class CustomDialogPreference(private val context_: Context, attrs: AttributeSet?) : android.preference.DialogPreference(context_, attrs), DialogInterface.OnClickListener {
    override fun onClick(dialog: DialogInterface, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            context_.sharedPrefs().edit(commit = true) {
                when (title) {
                    context_.resources.getString(R.string.deck_conf_reset) -> {
                        // Deck Options :: Restore Defaults for Options Group
                        putBoolean("confReset", true)
                    }

                    context_.resources.getString(R.string.dialog_positive_remove) -> {
                        // Deck Options :: Remove Options Group
                        putBoolean("confRemove", true)
                    }

                    context_.resources.getString(R.string.deck_conf_set_subdecks) -> {
                        // Deck Options :: Set Options Group for all Sub-decks
                        putBoolean("confSetSubdecks", true)
                    }
                }
            }
        }
    }
}
