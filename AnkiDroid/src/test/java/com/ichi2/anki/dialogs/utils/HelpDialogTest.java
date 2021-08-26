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


import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.RunInBackground;
import com.ichi2.anki.dialogs.HelpDialog;
import com.ichi2.anki.dialogs.RecursivePictureMenu;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class HelpDialogTest extends RobolectricTest {

    @Test
    @RunInBackground
    public void testMenuDoesNotCrash() {
        RecursivePictureMenu dialog = (RecursivePictureMenu) HelpDialog.createInstance(getTargetContext());

        super.openDialogFragmentUsingActivity(dialog);

        RecyclerView v = RecursivePictureMenuUtil.getRecyclerViewFor(dialog);

        assertThat(v.getChildCount(), is(4));
    }

    @Test
    @RunInBackground
    public void testmenuSupportAnkiDroidDoesNotCrash() {
        RecursivePictureMenu dialog = (RecursivePictureMenu) HelpDialog.createInstanceForSupportAnkiDroid(getTargetContext());

        super.openDialogFragmentUsingActivity(dialog);

        RecyclerView v = RecursivePictureMenuUtil.getRecyclerViewFor(dialog);

        assertThat(v.getChildCount(), is(5));
    }
}
