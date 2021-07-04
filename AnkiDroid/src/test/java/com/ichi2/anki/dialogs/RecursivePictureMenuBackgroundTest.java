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

package com.ichi2.anki.dialogs;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.dialogs.RecursivePictureMenu.Item;
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class RecursivePictureMenuBackgroundTest extends RobolectricTest {
    private final @NonNull RecursivePictureMenuDelegate mDelegate = new RecursivePictureMenuDelegate(this);

    @Test
    public void testNormalStartupSelectingItem() {
        Item linkedItem = mDelegate.getItemLinkingTo(R.string.link_anki);

        RecyclerView v = mDelegate.getRecyclerViewFor(linkedItem);
        mDelegate.clickChildAtIndex(v, 0);

        assertThat(mDelegate.getActivity().getLastUrlOpened(), is(getResourceString(R.string.link_anki)));
    }


    @Test
    public void testSelectingHeader() {
        int numberOfChildItems = 2;
        Item header = mDelegate.getHeaderWithSubItems(numberOfChildItems);
        RecyclerView v = mDelegate.getRecyclerViewFor(header);
        mDelegate.clickChildAtIndex(v, 0);

        RecursivePictureMenu currentMenu = (RecursivePictureMenu) mDelegate.getActivity().getLastShownDialogFragment();

        RecyclerView rv =  RecursivePictureMenuUtil.getRecyclerViewFor(currentMenu);

        assertThat("Unexpected number of items - check the adapter", rv.getChildCount(), is(numberOfChildItems));
    }
}