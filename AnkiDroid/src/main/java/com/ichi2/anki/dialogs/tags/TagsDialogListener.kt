// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.model.CardStateFilter

@KotlinCleanup("make selectedTags and indeterminateTags non-null")
interface TagsDialogListener {
    /**
     * Called when [TagsDialog] finished with selecting tags.
     *
     * @param selectedTags the list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * determining if tags in this list is checked or not is done by looking at the list of
     * previous tags. if the tag is found in both previous and indeterminate, it should be kept
     * otherwise it should be removed @see [com.ichi2.utils.TagsUtil.getUpdatedTags]
     * @param stateFilter selection radio option, should be ignored if not expected
     */
    fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    )

    fun <F> F.registerFragmentResultReceiver() where F : Fragment, F : TagsDialogListener {
        parentFragmentManager.setFragmentResultListener(
            ON_SELECTED_TAGS_KEY,
            this,
        ) { _: String?, bundle: Bundle ->
            val selectedTags: List<String> =
                bundle.getStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS)!!
            val indeterminateTags: List<String> =
                bundle.getStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS)!!
            val option = bundle.getSerializableCompat<CardStateFilter>(ON_SELECTED_TAGS__OPTION)!!
            onSelectedTags(selectedTags, indeterminateTags, option)
        }
    }

    companion object {
        fun createFragmentResultSender(fragmentManager: FragmentManager) =
            object : TagsDialogListener {
                override fun onSelectedTags(
                    selectedTags: List<String>,
                    indeterminateTags: List<String>,
                    stateFilter: CardStateFilter,
                ) {
                    val bundle =
                        Bundle().apply {
                            putStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS, ArrayList(selectedTags))
                            putStringArrayList(ON_SELECTED_TAGS__INDETERMINATE_TAGS, ArrayList(indeterminateTags))
                            putSerializable(ON_SELECTED_TAGS__OPTION, stateFilter)
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
