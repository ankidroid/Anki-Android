/****************************************************************************************
 * Copyright (c) 2018 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.anki;

import android.content.Context;
import android.content.res.Resources;

import com.ichi2.compat.Compat;
import com.ichi2.compat.CompatHelper;

public final class NotificationChannels {
    public enum Channel { GENERAL, SYNC, GLOBAL_REMINDERS, DECK_REMINDERS }

    public static String getId(Channel channel) {
        switch (channel) {
            case SYNC:
                return "Synchronization";
            case GLOBAL_REMINDERS:
                return "Global Reminders";
            case DECK_REMINDERS:
                return "Deck Reminders";
            case GENERAL:
            default:
                return "General Notifications";
        }
    }

    private static String getName(Channel channel, Resources res) {
        switch (channel) {
            case SYNC:
                return res.getString(R.string.sync_title);
            case GLOBAL_REMINDERS:
                return res.getString(R.string.widget_minimum_cards_due_notification_ticker_title);
            case DECK_REMINDERS:
                return res.getString(R.string.deck_conf_reminders);
            case GENERAL:
            default:
                return res.getString(R.string.app_name);
        }
    }

    /**
     * Create or update all the notification channels for the app
     *
     * TODO should be called in response to {@link android.content.Intent#ACTION_LOCALE_CHANGED}
     * @param context the context for access to localized strings for channel names
     */
    public static void setup(Context context) {
        Resources res = context.getResources();
        Compat compat = CompatHelper.getCompat();
        for (Channel channel : Channel.values()) {
            compat.setupNotificationChannel(context, getId(channel), getName(channel, res));
        }
    }
}
