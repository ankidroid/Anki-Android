/*
 Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

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
package com.ichi2.anki.dialogs.tags;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.RadioGroup;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.R;
import com.ichi2.anki.dialogs.tags.TagsDialog.DialogType;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.dialogs.tags.TagsDialogListener.*;
import static com.ichi2.testutils.ParametersUtils.whatever;
import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class TagsDialogTest {


    private static LifecycleOwner mockLifecycleOwner() {
        LifecycleOwner owner = mock(LifecycleOwner.class);
        LifecycleRegistry lifecycle = new LifecycleRegistry(owner);
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        when(owner.getLifecycle()).thenReturn(lifecycle);
        return owner;
    }


    @Test
    public void testTagsDialogCustomStudyOptionInterface() {

        final DialogType type = DialogType.CUSTOM_STUDY_TAGS;
        final List<String> allTags = Arrays.asList("1", "2", "3", "4");

        Bundle args = new TagsDialog(whatever())
                .withArguments(type, new ArrayList<>(), allTags)
                .getArguments();


        final TagsDialogListener mockListener = mock(TagsDialogListener.class);


        TagsDialogFactory factory = new TagsDialogFactory(mockListener);
        FragmentScenario<TagsDialog> scenario = FragmentScenario.launch(TagsDialog.class, args, R.style.Theme_AppCompat, factory);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment((f) -> {
            MaterialDialog dialog = (MaterialDialog) f.getDialog();
            assertThat(dialog, notNullValue());

            final View body = dialog.getCustomView();
            final RadioGroup mOptionsGroup  = body.findViewById(R.id.tags_dialog_options_radiogroup);

            assertEquals(mOptionsGroup.getVisibility(), View.VISIBLE);

            final int expectedOption = 1;

            mOptionsGroup.getChildAt(expectedOption).performClick();

            dialog.getActionButton(DialogAction.POSITIVE).callOnClick();

            verify(mockListener, times(1)).onSelectedTags(new ArrayList<>(), expectedOption);
        });
    }


    @Test
    public void testTagsDialogCustomStudyOptionFragmentAPI() {

        final DialogType type = DialogType.CUSTOM_STUDY_TAGS;
        final List<String> allTags = Arrays.asList("1", "2", "3", "4");

        Bundle args = new TagsDialog(whatever())
                .withArguments(type, new ArrayList<>(), allTags)
                .getArguments();



        FragmentScenario<TagsDialog> scenario = FragmentScenario.launch(TagsDialog.class, args, R.style.Theme_AppCompat);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment((f) -> {
            MaterialDialog dialog = (MaterialDialog) f.getDialog();
            assertThat(dialog, notNullValue());




            AtomicReference<List<String>> returnedList = new AtomicReference<>();
            AtomicInteger returnedOption = new AtomicInteger();

            f.getParentFragmentManager().setFragmentResultListener(ON_SELECTED_TAGS_KEY, mockLifecycleOwner(),
                    (requestKey, bundle) -> {
                        returnedList.set(bundle.getStringArrayList(ON_SELECTED_TAGS__TAGS));
                        returnedOption.set(bundle.getInt(ON_SELECTED_TAGS__OPTION));
                    });



            final View body = dialog.getCustomView();
            final RadioGroup mOptionsGroup  = body.findViewById(R.id.tags_dialog_options_radiogroup);

            assertEquals(mOptionsGroup.getVisibility(), View.VISIBLE);

            final int expectedOption = 2;

            mOptionsGroup.getChildAt(expectedOption).performClick();

            dialog.getActionButton(DialogAction.POSITIVE).callOnClick();

            assertListEquals(new ArrayList<>(), returnedList.get());
            assertEquals(expectedOption, returnedOption.get());
        });
    }


    // regression test #8762
    @Test
    public void test_AddNewTag_shouldBeVisibleInRecyclerView() {
        final DialogType type = DialogType.ADD_TAG;
        final List<String> allTags = Arrays.asList("a", "b", "d", "e");
        final List<String> checkedTags = Arrays.asList("a", "b");

        Bundle args = new TagsDialog(whatever())
                .withArguments(type, checkedTags, allTags)
                .getArguments();

        final TagsDialogListener mockListener = mock(TagsDialogListener.class);

        TagsDialogFactory factory = new TagsDialogFactory(mockListener);
        FragmentScenario<TagsDialog> scenario = FragmentScenario.launch(TagsDialog.class, args, R.style.Theme_AppCompat, factory);

        scenario.moveToState(Lifecycle.State.STARTED);

        scenario.onFragment((f) -> {
            MaterialDialog dialog = (MaterialDialog) f.getDialog();
            assertThat(dialog, notNullValue());

            final View body = dialog.getCustomView();
            RecyclerView recycler = body.findViewById(R.id.tags_dialog_tags_list);

            final String NEW_TAG = "c";

            f.addTag(NEW_TAG);

            // workaround robolectric recyclerView issue
            // update recycler
            recycler.measure(0, 0);
            recycler.layout(0, 0, 100, 1000);

            TagsArrayAdapter.ViewHolder vh = (TagsArrayAdapter.ViewHolder) recycler.findViewHolderForAdapterPosition(2);
            CheckedTextView itemView = (CheckedTextView) vh.itemView;

            assertEquals(NEW_TAG, itemView.getText());
            assertTrue(itemView.isChecked());
        });
    }

}