/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.anki.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


import com.ichi2.anki.CollectionHelper;
import com.ichi2.libanki.Collection;

import timber.log.Timber;

/**
 * This Broadcast-Receiver listens to media ejects and closes the collection prior to unmount. It then sends a broadcast
 * intent to all activities which might be open in order to show an appropriate screen After media has been remounted,
 * another broadcast intent will be sent to let the activites know about it
 */

public class SdCardReceiver extends BroadcastReceiver {

    public static final String MEDIA_EJECT = "com.ichi2.anki.action.MEDIA_EJECT";
    public static final String MEDIA_MOUNT = "com.ichi2.anki.action.MEDIA_MOUNT";


    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_MEDIA_EJECT)) {
            Timber.i("media eject detected - closing collection and sending broadcast");
            Intent i = new Intent();
            i.setAction(MEDIA_EJECT);
            context.sendBroadcast(i);
            try {
                Collection col = CollectionHelper.getInstance().getCol(context);
                if (col != null) {
                    col.close();
                }
            } catch (Exception e) {
                Timber.w(e, "Exception while trying to close collection likely because it was already unmounted");
            }
        } else if (intent.getAction().equals(Intent.ACTION_MEDIA_MOUNTED)) {
            Timber.i("media mount detected - sending broadcast");
            Intent i = new Intent();
            i.setAction(MEDIA_MOUNT);
            context.sendBroadcast(i);
        }
    }

}
