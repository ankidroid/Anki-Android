/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer;

import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;

import com.ichi2.anki.R;
import com.ichi2.ui.ActionBarOverflow;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class ActionButtons
{
    private final ActionButtonStatus mActionButtonStatus;

    @IdRes
    public static final int RES_FLAG = R.id.action_flag;
    @IdRes
    public static final int RES_MARK = R.id.action_mark_card;

    private Menu mMenu;

    public ActionButtons(ReviewerUi reviewerUi) {
        this.mActionButtonStatus = new ActionButtonStatus(reviewerUi);
    }

    public void setup(SharedPreferences preferences) {
        this.mActionButtonStatus.setup(preferences);
    }

    /** Sets the order of the Action Buttons in the action bar */
    public void setCustomButtonsStatus(Menu menu) {
        this.mActionButtonStatus.setCustomButtons(menu);
        this.mMenu = menu;
    }

    public @Nullable Boolean isShownInActionBar(@IdRes int resId) {
        MenuItem menuItem = findMenuItem(resId);
        if (menuItem == null) {
            return null;
        }
        //firstly, see if we can definitively determine whether the action is visible.
        Boolean isActionButton = ActionBarOverflow.isActionButton(menuItem);
        if (isActionButton != null) {
            return isActionButton;
        }
        //If not, use heuristics based on preferences.
        return isLikelyActionButton(resId);
    }


    private @Nullable MenuItem findMenuItem(@IdRes int resId) {
        if (mMenu == null) {
            return null;
        }
        return mMenu.findItem(resId);
    }


    private boolean isLikelyActionButton(@IdRes int resourceId) {
        /*
        https://github.com/ankidroid/Anki-Android/pull/5918#issuecomment-609484093
        Heuristic approach: Show the item in the top bar unless the corresponding menu item is set to "always" show.

        There are two scenarios where the heuristic fails:

        1. An item is set to 'if room' but is actually visible in the toolbar
        2. An item is set to 'always' but is actually not visible in the toolbar

        Failure scenario one is totally acceptable IMO as it just falls back to the current behavior.
        Failure scenario two is not ideal, but it should only happen in the pathological case where the user has gone
        and explicitly changed the preferences to set more items to 'always' than there is room for in the toolbar.

        In any case, both failure scenarios only happen if the user deviated from the default settings in strange ways.
         */
        Integer status = mActionButtonStatus.getByMenuResourceId(resourceId);
        if (status == null) {
            Timber.w("Failed to get status for resource: %d", resourceId);
            //If we return "true", we may hide the flag/mark status completely. False is safer.
            return false;
        }
        return status == ActionButtonStatus.SHOW_AS_ACTION_ALWAYS;
    }

    public ActionButtonStatus getStatus() {
        //DEFECT: This should be private - it breaks the law of demeter, but it'll be a large refactoring to get
        // to this point
        return this.mActionButtonStatus;
    }
}
