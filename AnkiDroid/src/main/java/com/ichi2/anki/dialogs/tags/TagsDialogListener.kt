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
package com.ichi2.anki.dialogs.tags

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentResultListener
import com.ichi2.utils.KotlinCleanup
import java.util.ArrayList

@KotlinCleanup("make selectedTags and indeterminateTags non-null")
interface TagsDialogListener {
    /**
     * Called when [TagsDialog] finished with selecting tags.
     *
     * @param selectedTags the list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * determining if tags in this list is checked or not is done by looking at the list of
     * previous tags. if the tag is found in both previous and indeterminate, it should be kept
     * otherwise it should be removed @see [TagsUtil.getUpdatedTags]
     * @param option selection radio option, should be ignored if not expected
     */
    fun onSelectedTags(selectedTags: List<String>, indeterminateTags: List<String>, option: Int)
    fun <F> F.registerFragmentResultReceiver() where F : Fragment, F : TagsDialogListener {
        parentFragmentManager.setFragmentResultListener(
            ON_SELECTED_TAGS_KEY, this,
            FragmentResultListener { _: String?, bundle: Bundle ->
                val selectedTags: List<String> = bundle.getStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS)!!
                val indeterminateTags: List<String> = bundle.getStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS)!!
                val option = bundle.getInt(ON_SELECTED_TAGS__OPTION)
                onSelectedTags(selectedTags, indeterminateTags, option)
            }
        )
    }

    companion object {
        fun createFragmentResultSender(fragmentManager: FragmentManager) = object : TagsDialogListener {
            override fun onSelectedTags(selectedTags: List<String>, indeterminateTags: List<String>, option: Int) {
                val bundle = Bundle().apply {
                    putStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS, ArrayList(selectedTags))
                    putStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS, ArrayList(indeterminateTags))
                    putInt(ON_SELECTED_TAGS__OPTION, option)
                }
                fragmentManager.setFragmentResult(ON_SELECTED_TAGS_KEY, bundle)
            }
        }

        const val ON_SELECTED_TAGS_KEY = "ON_SELECTED_TAGS_KEY"
        const val ON_SELECTED_TAGS__SELECTED_TAGS = "SELECTED_TAGS"
        const val ON_SELECTED_TAGS__INDETERMINATE_TAGS = "INDETERMINATE_TAGS"
        const val ON_SELECTED_TAGS__OPTION = "OPTION"
    }
}
