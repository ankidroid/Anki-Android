/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
 * Copyright (c) 2015 Timothy Rae <timothy.rae@gmail.com>                               *
 * Copyright (c) 2016 Mark Carter <mark@marcardar.com>                                  *
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

package com.ichi2.anki.provider.legacy;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.provider.BaseCardContentProvider;

import timber.log.Timber;

/**
 * Legacy ContentProvider that is designed to use a more relaxed permissions scheme than the newer provider in order to
 * work around the following design flaw in the Android framework on Android SDK 22 and below:
 * <a href="https://code.google.com/p/android/issues/detail?id=25906">Issue 25906:	Permissions Are Install-Order Dependent</a>
 * This provider is disabled on Android Marshmallow and above.
 */
@Deprecated
public final class CardContentProvider extends BaseCardContentProvider {
    private static final int VER = 1;
    static {
        // Here you can see all the URIs at a glance
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes", NOTES);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#", NOTES_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#/cards", NOTES_ID_CARDS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "notes/#/cards/#", NOTES_ID_CARDS_ORD);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models", MODELS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*", MODELS_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*/templates", MODELS_ID_TEMPLATES);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "models/*/templates/#", MODELS_ID_TEMPLATES_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "schedule/", SCHEDULE);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "decks/", DECKS);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "decks/#", DECKS_ID);
        sUriMatcher.addURI(FlashCardsContract.getAuthority(VER), "selected_deck/", DECK_SELECTED);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Context c = getContext();
        if (c == null) {
            return 0;
        }
        if (c.checkCallingPermission(FlashCardsContract.READ_WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Update permission not granted for: " + uri);
        }
        return super.update(uri, values, selection, selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Context c = getContext();
        if (c == null) {
            return 0;
        }
        if (c.checkCallingPermission(FlashCardsContract.READ_WRITE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Delete permission not granted for: " + uri);
        }
        return super.delete(uri, selection, selectionArgs);
    }

    /**
     * Enable or disable the ContentProvider
     */
    public static void setEnabledState(Context context, boolean enabled) {
        ComponentName providerName = new ComponentName(context, "com.ichi2.anki.provider.legacy.CardContentProvider");
        PackageManager pm = context.getPackageManager();
        int state = (enabled) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP);
    }
}
