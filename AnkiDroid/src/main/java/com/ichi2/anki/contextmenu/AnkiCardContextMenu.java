/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.contextmenu;

import android.content.Context;

import androidx.annotation.NonNull;

public class AnkiCardContextMenu extends SystemContextMenu {


    public static final String ANKI_CARD_CONTEXT_MENU_PREF_KEY = "anki_card_enable_external_context_menu";


    @SuppressWarnings("WeakerAccess")
    public AnkiCardContextMenu(@NonNull Context context) {
        super(context);
    }

    public static void ensureConsistentStateWithSharedPreferences(@NonNull Context context) {
        new AnkiCardContextMenu(context).ensureConsistentStateWithSharedPreferences();
    }

    @NonNull
    @Override
    protected String getActivityName() {
        return "com.ichi2.anki.AnkiCardContextMenuAction";
    }

    @Override
    protected boolean getDefaultEnabledStatus() {
        return false;
    }

    @NonNull
    @Override
    protected String getPreferenceKey() {
        return ANKI_CARD_CONTEXT_MENU_PREF_KEY;
    }
}
