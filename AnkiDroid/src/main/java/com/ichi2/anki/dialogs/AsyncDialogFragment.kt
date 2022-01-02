/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
import android.content.res.Resources;
import android.os.Message;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.analytics.AnalyticsDialogFragment;

import timber.log.Timber;

public abstract class AsyncDialogFragment extends AnalyticsDialogFragment {
    /* provide methods for text to show in notification bar when the DialogFragment
       can't be shown due to the host activity being in stopped state.
       This can happen when the DialogFragment is shown from 
       the onPostExecute() method of an AsyncTask */
    
    public abstract String getNotificationMessage();
    public abstract String getNotificationTitle();

    public Message getDialogHandlerMessage() {
        return null;
    }

    protected Resources res() {
        try {
            return AnkiDroidApp.getAppResources();
        } catch (Exception e) {
            Timber.w(e, "AnkiDroidApp.getAppResources failure. Returning Fragment resources as fallback.");
            return getResources();
        }
    }
} 