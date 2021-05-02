/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

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

import com.ichi2.utils.TagsUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public interface TagsDialogListener {

    String ON_SELECTED_TAGS_KEY = "ON_SELECTED_TAGS_KEY";
    String ON_SELECTED_TAGS__SELECTED_TAGS = "SELECTED_TAGS";
    String ON_SELECTED_TAGS__INDETERMINATE_TAGS = "INDETERMINATE_TAGS";
    String ON_SELECTED_TAGS__OPTION = "OPTION";

    /**
     * Called when {@link TagsDialog} finished with selecting tags.
     *
     * @param selectedTags the list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     *                          determining if tags in this list is checked or not is done by looking at the list of
     *                          previous tags. if the tag is found in both previous and indeterminate, it should be kept
     *                          otherwise it should be removed @see {@link TagsUtil#getUpdatedTags(List, List, List)}
     * @param option selection radio option, should be ignored if not expected
     */
    void onSelectedTags(List<String> selectedTags, List<String> indeterminateTags, int option);


    default <F extends Fragment & TagsDialogListener> void registerFragmentResultReceiver(F fragment) {
        fragment.getParentFragmentManager().setFragmentResultListener(
                ON_SELECTED_TAGS_KEY, fragment,
                (requestKey, bundle) -> {
                    final List<String> selectedTags = bundle.getStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS);
                    final List<String> indeterminateTags = bundle.getStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS);
                    final int option = bundle.getInt(ON_SELECTED_TAGS__OPTION);
                    fragment.onSelectedTags(selectedTags, indeterminateTags, option);
                }
        );
    }

    static TagsDialogListener createFragmentResultSender(FragmentManager fragmentManager) {
        return new TagsDialogListener() {
            @Override
            public void onSelectedTags(List<String> selectedTags, List<String> indeterminateTags, int option) {
                final Bundle bundle = new Bundle();
                bundle.putStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS, new ArrayList<>(selectedTags));
                bundle.putStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS, new ArrayList<>(indeterminateTags));
                bundle.putInt(ON_SELECTED_TAGS__OPTION, option);
                fragmentManager.setFragmentResult(ON_SELECTED_TAGS_KEY, bundle);
            }
        };
    }
}
