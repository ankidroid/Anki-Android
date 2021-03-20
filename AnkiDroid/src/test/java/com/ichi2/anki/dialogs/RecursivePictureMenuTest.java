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

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.testutils.AnkiAssert.assertDoesNotThrow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class RecursivePictureMenuTest extends RobolectricTest {

    private FragmentTestActivity mActivity;

    @Test
    public void testNormalStartupSelectingItem() {
        Item linkedItem = getItemLinkingTo(R.string.link_anki);

        RecyclerView v = getRecyclerViewFor(linkedItem);
        clickChildAtIndex(v, 0);

        assertThat(mActivity.getLastUrlOpened(), is(getResourceString(R.string.link_anki)));
    }


    @Test
    public void testSelectingHeader() {
        int numberOfChildItems = 2;
        Item header = getHeaderWithSubItems(numberOfChildItems);
        RecyclerView v = getRecyclerViewFor(header);
        clickChildAtIndex(v, 0);

        RecursivePictureMenu currentMenu = (RecursivePictureMenu) mActivity.getLastShownDialogFragment();

        RecyclerView rv =  RecursivePictureMenuUtil.getRecyclerViewFor(currentMenu);

        assertThat("Unexpected number of items - check the adapter", rv.getChildCount(), is(numberOfChildItems));
    }


    @Test
    @Ignore("Not implemented")
    public void removeFromRoot() {
        Item header = getHeaderWithSubItems(1);


        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        RecursivePictureMenu.removeFrom(allItems, header);

        // Do we return, or check to see if the list is mutated?
    }

    @Test
    public void removeChild() {
        ItemHeader header = getHeaderWithSubItems(1);

        Item child = header.getChildren().get(0);

        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        RecursivePictureMenu.removeFrom(allItems, child);

        assertThat("child should be removed", header.getChildren(), hasSize(0));
    }

    @Test
    public void removeNotExisting() {
        ItemHeader header = getHeaderWithSubItems(1);

        ArrayList<Item> allItems = ArrayUtil.toArrayList(new Item[] { header });
        assertDoesNotThrow(() -> RecursivePictureMenu.removeFrom(allItems, getItemLinkingTo(R.string.link_anki_manual)));
    }

    private RecyclerView getRecyclerViewFor(Item... items) {
        ArrayList<Item> itemList = new ArrayList<>(Arrays.asList(items));
        RecursivePictureMenu menu = RecursivePictureMenu.createInstance(itemList, R.string.help);

        mActivity = openDialogFragmentUsingActivity(menu);


        return RecursivePictureMenuUtil.getRecyclerViewFor(menu);
    }



    protected void clickChildAtIndex(RecyclerView v, @SuppressWarnings("SameParameterValue") int index) {
        advanceRobolectricLooperWithSleep();
        View childAt = v.getChildAt(index); // This is null without appropriate looper calls
        childAt.performClick();
    }

    private Item getItemLinkingTo(int linkLocation) {
        return new LinkItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, linkLocation);
    }


    private ItemHeader getHeaderWithSubItems(int count) {

        Item[] items = new Item[count];
        for(int i = 0; i < count; i++) {
            items[i] = getItemLinkingTo(R.string.link_anki);
        }

        return new ItemHeader(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, items);
    }
}
