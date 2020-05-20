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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import com.ichi2.anki.AnkiDroidApp;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

import static android.content.pm.PackageManager.*;

public class CardBrowserContextMenu {

    @NonNull
    private final Context mContext;
    private static final boolean DEFAULT_ENABLED_STATUS = true;
    public static final String CARD_BROWSER_CONTEXT_MENU_PREF_KEY = "card_browser_enable_external_context_menu";
    /** We define an activity alias so we can disable the context menu without disabling the activity */
    private static final String ACTIVITY_ALIAS_NAME = "com.ichi2.anki.CardBrowserContextMenuAction";

    @SuppressWarnings("WeakerAccess")
    public CardBrowserContextMenu(@NonNull Context context) {
        this.mContext = context;
    }

    public static void ensureConsistentStateWithSharedPreferences(@NonNull Context context) {
        new CardBrowserContextMenu(context).ensureConsistentStateWithSharedPreferences();
    }

    @CheckResult
    @Nullable
    private Boolean getSystemMenuStatus() {
        try {
            return getPackageManager().getComponentEnabledSetting(getComponentName()) == COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Exception e) {
            Timber.w(e, "Failed to read context menu status setting");
            return null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void setSystemMenuEnabled(boolean enabled) {
        try {
            int enabledState = enabled ? COMPONENT_ENABLED_STATE_ENABLED : COMPONENT_ENABLED_STATE_DISABLED;
            getPackageManager().setComponentEnabledSetting(getComponentName(), enabledState, DONT_KILL_APP);
        } catch (Exception e) {
            Timber.w(e, "Failed to set Context Menu state");
        }
    }


    private PackageManager getPackageManager() {
        return mContext.getPackageManager();
    }


    //this can throw if context.getPackageName() throws
    @CheckResult
    private ComponentName getComponentName() {
        return new ComponentName(mContext, ACTIVITY_ALIAS_NAME);
    }


    private boolean getPreferenceStatus() {
        return AnkiDroidApp.getSharedPrefs(mContext).getBoolean(CARD_BROWSER_CONTEXT_MENU_PREF_KEY, DEFAULT_ENABLED_STATUS);
    }

    @SuppressWarnings("WeakerAccess")
    public void ensureConsistentStateWithSharedPreferences() {
        boolean preferenceStatus = getPreferenceStatus();
        Boolean actualStatus = getSystemMenuStatus();
        if (actualStatus == null || actualStatus != preferenceStatus) {
            Timber.d("Modifying Context Menu Status: Preference was %b", preferenceStatus);
            setSystemMenuEnabled(preferenceStatus);
        }
    }
}
