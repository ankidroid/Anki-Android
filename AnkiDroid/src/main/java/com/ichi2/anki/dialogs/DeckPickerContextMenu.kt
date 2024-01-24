/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.bundleOf
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.libanki.DeckId
import com.ichi2.utils.title

class DeckPickerContextMenu : AnalyticsDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        assert(requireArguments().containsKey(ARG_DECK_ID))
        assert(requireArguments().containsKey(ARG_DECK_NAME))
        assert(requireArguments().containsKey(ARG_DECK_IS_DYN))
        assert(requireArguments().containsKey(ARG_DECK_HAS_BURIED_IN_DECK))
        val options = createOptionsList()
        return AlertDialog.Builder(ContextThemeWrapper(requireActivity(), R.style.AlertDialogStyle))
            .title(text = requireArguments().getString(ARG_DECK_NAME))
            .setItems(
                options.map { resources.getString(it.optionName) }.toTypedArray()
            ) { _, index: Int ->
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY_CONTEXT_MENU,
                    bundleOf(
                        CONTEXT_MENU_DECK_ID to requireArguments().getLong(ARG_DECK_ID),
                        CONTEXT_MENU_DECK_OPTION to options[index]
                    )
                )
            }
            .create()
    }

    private fun createOptionsList(): List<DeckPickerContextMenuOption> =
        mutableListOf<DeckPickerContextMenuOption>().apply {
            val dyn = requireArguments().getBoolean(ARG_DECK_IS_DYN)
            add(DeckPickerContextMenuOption.ADD_CARD)
            add(DeckPickerContextMenuOption.BROWSE_CARDS)
            if (dyn) {
                add(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD)
                add(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY)
            }
            add(DeckPickerContextMenuOption.RENAME_DECK)
            if (!dyn) {
                add(DeckPickerContextMenuOption.CREATE_SUBDECK)
            }
            add(DeckPickerContextMenuOption.DECK_OPTIONS)
            if (!dyn) {
                add(DeckPickerContextMenuOption.CUSTOM_STUDY)
            }
            add(DeckPickerContextMenuOption.EXPORT_DECK)
            if (requireArguments().getBoolean(ARG_DECK_HAS_BURIED_IN_DECK)) {
                add(DeckPickerContextMenuOption.UNBURY)
            }
            add(DeckPickerContextMenuOption.CREATE_SHORTCUT)
            add(DeckPickerContextMenuOption.DELETE_DECK)
        }

    enum class DeckPickerContextMenuOption(@StringRes val optionName: Int) {
        RENAME_DECK(R.string.rename_deck),
        DECK_OPTIONS(R.string.menu__deck_options),
        CUSTOM_STUDY(R.string.custom_study),
        DELETE_DECK(R.string.contextmenu_deckpicker_delete_deck),
        EXPORT_DECK(R.string.export_deck),
        UNBURY(R.string.unbury),
        CUSTOM_STUDY_REBUILD(R.string.rebuild_cram_label),
        CUSTOM_STUDY_EMPTY(R.string.empty_cram_label),
        CREATE_SUBDECK(R.string.create_subdeck),
        CREATE_SHORTCUT(R.string.create_shortcut),
        BROWSE_CARDS(R.string.browse_cards),
        ADD_CARD(R.string.menu_add);
    }

    companion object {
        const val REQUEST_KEY_CONTEXT_MENU = "request_key_context_menu"
        const val CONTEXT_MENU_DECK_OPTION = "context_menu_deck_option"
        const val CONTEXT_MENU_DECK_ID = "context_menu_deck_id"

        @VisibleForTesting
        const val ARG_DECK_ID = "arg_deck_id"

        @VisibleForTesting
        const val ARG_DECK_NAME = "arg_deck_name"

        @VisibleForTesting
        const val ARG_DECK_IS_DYN = "arg_deck_is_dyn"

        @VisibleForTesting
        const val ARG_DECK_HAS_BURIED_IN_DECK = "arg_deck_has_buried_in_deck"

        fun newInstance(
            id: DeckId,
            name: String,
            isDynamic: Boolean,
            hasBuriedInDeck: Boolean
        ): DeckPickerContextMenu = DeckPickerContextMenu().apply {
            arguments = bundleOf(
                ARG_DECK_ID to id,
                ARG_DECK_NAME to name,
                ARG_DECK_IS_DYN to isDynamic,
                ARG_DECK_HAS_BURIED_IN_DECK to hasBuriedInDeck
            )
        }
    }
}
