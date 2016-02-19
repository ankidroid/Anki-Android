/***************************************************************************************
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

package com.ichi2.libanki.hooks;

import android.app.Activity;
import android.content.res.Resources;

import android.widget.Toast;

import com.ichi2.anki.R;
import com.ichi2.libanki.Card;

import timber.log.Timber;

/**
 * Class used to display toast when leech is made
 */
public class Leech {
    public class LeechHook extends Hook {
        @Override
        public void runHook(Object... args) {
            Card card = (Card) args[0];
            final Activity activity = (Activity) args[1];
            if (activity != null) {
                Resources res = activity.getResources();
                final String leechMessage;
                if (card.getQueue() < 0) {
                    leechMessage = res.getString(R.string.leech_suspend_notification);
                } else {
                    leechMessage = res.getString(R.string.leech_notification);
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, leechMessage, Toast.LENGTH_SHORT).show();
                    }
                });

            } else {
                Timber.e("LeechHook :: could not show leech toast as activity was null");
            }
        }
    }


    public void installHook(Hooks h) {
        h.addHook("leech", new LeechHook());
    }
}
