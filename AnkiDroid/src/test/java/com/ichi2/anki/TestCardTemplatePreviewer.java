/*
 *  Copyright (c) 2021 Mike Hardy <github@mikehardy.net>
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

package com.ichi2.anki;

import android.view.View;

public class TestCardTemplatePreviewer extends CardTemplatePreviewer {
    protected boolean mShowingAnswer = false;
    public boolean getShowingAnswer() { return mShowingAnswer; }
    public void disableDoubleClickPrevention() {
        mLastClickTime = (AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt(DOUBLE_TAP_TIME_INTERVAL, DEFAULT_DOUBLE_TAP_TIME_INTERVAL) * -2);
    }


    @Override
    protected void displayCardAnswer() {
        super.displayCardAnswer();
        mShowingAnswer = true;
    }


    @Override
    public void displayCardQuestion() {
        super.displayCardQuestion();
        mShowingAnswer = false;
    }

    public Boolean nextButtonVisible() {
        return mPreviewLayout.getNextCard().getVisibility() != View.GONE;
    }

    public Boolean previousButtonVisible() {
        return mPreviewLayout.getPrevCard().getVisibility() != View.GONE;
    }


    public Boolean previousButtonEnabled() {
        return mPreviewLayout.getPrevCard().isEnabled();
    }


    public Boolean nextButtonEnabled() {
        return mPreviewLayout.getNextCard().isEnabled();
    }
}
