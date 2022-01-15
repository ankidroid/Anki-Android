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

package com.ichi2.anki.dialogs.utils;

import android.net.Uri;

import com.ichi2.anki.AnkiActivity;
import com.ichi2.anki.RobolectricTest;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class FragmentTestActivity extends AnkiActivity {

    private String mLastUriOpened = null;
    private DialogFragment mLastShownDialogFragment;


    public String getLastUrlOpened() {
        return mLastUriOpened;
    }


    @Override
    public void openUrl(@NonNull Uri url) {
        mLastUriOpened = url.toString();
        super.openUrl(url);
    }


    @Override
    public void showDialogFragment(DialogFragment newFragment) {
        super.showDialogFragment(newFragment);
        mLastShownDialogFragment = newFragment;
        // Note: I saw a potential solution for this sleeping on StackOverflow - can't find the code again.
        RobolectricTest.advanceRobolectricLooperWithSleep(); // 6 of normal advance wasn't enough
        RobolectricTest.advanceRobolectricLooperWithSleep(); // 1 sleep wasn't enough :/
    }


    public DialogFragment getLastShownDialogFragment() {
        return mLastShownDialogFragment;
    }
}
