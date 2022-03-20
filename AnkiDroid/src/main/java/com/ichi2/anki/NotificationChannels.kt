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

package com.ichi2.anki

import android.content.Context
import android.content.res.Resources
import com.ichi2.compat.CompatHelper

object NotificationChannels {
    @JvmStatic
    fun getId(channel: Channel?): String {
        return when (channel) {
            Channel.SYNC -> "Synchronization"
            Channel.GLOBAL_REMINDERS -> "Global Reminders"
            Channel.DECK_REMINDERS -> "Deck Reminders"
            Channel.GENERAL -> "General Notifications"
            else -> "General Notifications"
        }
    }

    private fun getName(channel: Channel?, res: Resources): String {
        return when (channel) {
            Channel.SYNC -> res.getString(R.string.sync_title)
            Channel.GLOBAL_REMINDERS -> res.getString(R.string.widget_minimum_cards_due_notification_ticker_title)
            Channel.DECK_REMINDERS -> res.getString(R.string.deck_conf_reminders)
            Channel.GENERAL -> res.getString(R.string.app_name)
            else -> res.getString(R.string.app_name)
        }
    }

    /**
     * Create or update all the notification channels for the app
     *
     * TODO should be called in response to {@link android.content.Intent#ACTION_LOCALE_CHANGED}
     * @param context the context for access to localized strings for channel names
     */
    @JvmStatic
    fun setup(context: Context) {
        val res = context.resources
        val compat = CompatHelper.compat
        for (channel in Channel.values()) {
            compat.setupNotificationChannel(context, getId(channel), getName(channel, res))
        }
    }

    enum class Channel {
        GENERAL, SYNC, GLOBAL_REMINDERS, DECK_REMINDERS
    }
}
