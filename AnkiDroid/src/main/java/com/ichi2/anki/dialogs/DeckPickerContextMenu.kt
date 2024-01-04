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
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.requireLong
import com.ichi2.utils.ExtendedFragmentFactory
import java.util.function.Supplier

class DeckPickerContextMenu(private val collection: Collection) : AnalyticsDialogFragment() {

    fun withArguments(did: DeckId): DeckPickerContextMenu {
        val args = this.arguments ?: Bundle()
        args.putLong("did", did)
        this.arguments = args
        return this
    }

    /** The selected deck for the context menu */
    val deckId get() = requireArguments().requireLong("did")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val title = collection.decks.name(deckId)
        return MaterialDialog(requireActivity())
            .title(text = title)
            .cancelable(true)
            .noAutoDismiss()
            .listItems(items = contextMenuOptions.map { resources.getString(it.optionName) }) {
                    _: MaterialDialog, index: Int, _: CharSequence ->
                (requireActivity() as DeckPicker).handleContextMenuSelection(contextMenuOptions[index], deckId)
            }
    }

    /**
     * Retrieve the list of menu options to put in the context menu.
     */
    private val contextMenuOptions: List<DeckPickerContextMenuOption>
        get() {
            val did = deckId
            val dyn = collection.decks.isDyn(did)
            val contextMenuOptions = ArrayList<DeckPickerContextMenuOption>(11) // init with our fixed list size for performance
            contextMenuOptions.add(DeckPickerContextMenuOption.ADD_CARD)
            contextMenuOptions.add(DeckPickerContextMenuOption.BROWSE_CARDS)
            if (dyn) {
                contextMenuOptions.add(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD)
                contextMenuOptions.add(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY)
            }
            contextMenuOptions.add(DeckPickerContextMenuOption.RENAME_DECK)
            if (!dyn) {
                contextMenuOptions.add(DeckPickerContextMenuOption.CREATE_SUBDECK)
            }
            contextMenuOptions.add(DeckPickerContextMenuOption.DECK_OPTIONS)
            if (!dyn) {
                contextMenuOptions.add(DeckPickerContextMenuOption.CUSTOM_STUDY)
            }
            contextMenuOptions.add(DeckPickerContextMenuOption.EXPORT_DECK)
            if (collection.sched.haveBuriedInCurrentDeck()) {
                contextMenuOptions.add(DeckPickerContextMenuOption.UNBURY)
            }
            contextMenuOptions.add(DeckPickerContextMenuOption.CREATE_SHORTCUT)
            contextMenuOptions.add(DeckPickerContextMenuOption.DELETE_DECK)
            return contextMenuOptions
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

    class Factory(val collectionSupplier: Supplier<Collection>) : ExtendedFragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            val cls = loadFragmentClass(classLoader, className)
            return if (cls == DeckPickerContextMenu::class.java) {
                newDeckPickerContextMenu()
            } else {
                super.instantiate(classLoader, className)
            }
        }

        private fun newDeckPickerContextMenu(): DeckPickerContextMenu =
            DeckPickerContextMenu(collectionSupplier.get())

        fun newDeckPickerContextMenu(deckId: DeckId): DeckPickerContextMenu =
            DeckPickerContextMenu(collectionSupplier.get()).withArguments(deckId)
    }
}
