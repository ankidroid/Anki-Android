package com.ichi2.anki.dialogs;

import android.view.View;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricAbstractTest;
import com.ichi2.anki.RobolectricForegroundTest;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.anki.dialogs.utils.FragmentTestActivity;
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil;

import java.util.ArrayList;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class RecursivePictureMenuDelegate {
    private final @NonNull
    RobolectricAbstractTest mRobolectric;

    private FragmentTestActivity mActivity;


    public RecursivePictureMenuDelegate(@NonNull RobolectricAbstractTest robolectric) {
        mRobolectric = robolectric;
    }


    public FragmentTestActivity getActivity() {
        return mActivity;
    }

    RecursivePictureMenu.Item getItemLinkingTo(int linkLocation) {
        return new HelpDialog.LinkItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, linkLocation);
    }


    RecursivePictureMenu.ItemHeader getHeaderWithSubItems(int count) {

        RecursivePictureMenu.Item[] items = new RecursivePictureMenu.Item[count];
        for(int i = 0; i < count; i++) {
            items[i] = getItemLinkingTo(R.string.link_anki);
        }

        return new RecursivePictureMenu.ItemHeader(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, items);
    }

    RecyclerView getRecyclerViewFor(RecursivePictureMenu.Item... items) {
        ArrayList<RecursivePictureMenu.Item> itemList = new ArrayList<>(Arrays.asList(items));
        RecursivePictureMenu menu = RecursivePictureMenu.createInstance(itemList, R.string.help);

        mActivity = mRobolectric.openDialogFragmentUsingActivity(menu);


        return RecursivePictureMenuUtil.getRecyclerViewFor(menu);
    }



    protected void clickChildAtIndex(RecyclerView v, @SuppressWarnings("SameParameterValue") int index) {
        View childAt = v.getChildAt(index); // This is null without appropriate looper calls
        childAt.performClick();
    }
}
