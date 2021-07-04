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

import android.view.View;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.dialogs.HelpDialog.LinkItem;
import com.ichi2.anki.dialogs.RecursivePictureMenu.Item;
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader;
import com.ichi2.anki.dialogs.utils.FragmentTestActivity;
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil;
import com.ichi2.utils.ArrayUtil;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class RecursivePictureMenuTest extends RobolectricTest {
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


    @Test
    @Ignore("Not implemented")
    public void removeFromRoot() {
        Item header = mDelegate.getHeaderWithSubItems(1);


        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        RecursivePictureMenu.removeFrom(allItems, header);

        // Do we return, or check to see if the list is mutated?
    }

    @Test
    public void removeChild() {
        ItemHeader header = mDelegate.getHeaderWithSubItems(1);

        Item child = header.getChildren().get(0);

        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        RecursivePictureMenu.removeFrom(allItems, child);

        assertThat("child should be removed", header.getChildren(), hasSize(0));
    }

    @Test
    public void removeNotExisting() {
        ItemHeader header = mDelegate.getHeaderWithSubItems(1);

        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        assertDoesNotThrow(() -> RecursivePictureMenu.removeFrom(allItems, mDelegate.getItemLinkingTo(R.string.link_anki_manual)));
    }

}
