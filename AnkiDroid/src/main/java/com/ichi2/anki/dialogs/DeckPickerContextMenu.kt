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
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.*
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.libanki.Collection
import com.ichi2.utils.BundleUtils.requireLong
import com.ichi2.utils.ExtendedFragmentFactory
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import java.util.function.Supplier

class DeckPickerContextMenu(private val collection: Collection) : AnalyticsDialogFragment() {

    fun withArguments(did: Long): DeckPickerContextMenu {
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
        val itemIds = contextMenuOptions.map { it.itemId }.toIntArray()
        return MaterialDialog.Builder(requireActivity())
            .title(title)
            .cancelable(true)
            .autoDismiss(false)
            .itemsIds(itemIds)
            .items(contextMenuOptions.map { resources.getString(it.optionName) })
            .itemsCallback(mContextMenuListener)
            .build()
    }

    /**
     * Retrieve the list of menu options to put in the context menu.
     */
    private val contextMenuOptions: List<DeckPickerContextMenuOption>
        get() {
            val did = deckId
            val dyn = collection.decks.isDyn(did)
            val contextMenuOptions = ArrayList<DeckPickerContextMenuOption>(11) // init with our fixed list size for performance
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
            if (collection.sched.haveBuried(did)) {
                contextMenuOptions.add(DeckPickerContextMenuOption.UNBURY)
            }
            contextMenuOptions.add(DeckPickerContextMenuOption.CREATE_SHORTCUT)
            contextMenuOptions.add(DeckPickerContextMenuOption.DELETE_DECK)
            return contextMenuOptions
        }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private val mContextMenuListener = ListCallback { _: MaterialDialog?, view: View, _: Int, _: CharSequence? ->
        when (DeckPickerContextMenuOption.fromId(view.id)) {
            DeckPickerContextMenuOption.DELETE_DECK -> {
                Timber.i("Delete deck selected")
                (activity as DeckPicker?)!!.confirmDeckDeletion(deckId)
            }
            DeckPickerContextMenuOption.DECK_OPTIONS -> {
                Timber.i("Open deck options selected")
                (activity as DeckPicker?)!!.showContextMenuDeckOptions(deckId)
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY -> {
                Timber.i("Custom study option selected")
                val ankiActivity = requireActivity() as AnkiActivity
                val d = FragmentFactoryUtils.instantiate(ankiActivity, CustomStudyDialog::class.java)
                d.withArguments(CustomStudyDialog.ContextMenuConfiguration.STANDARD, deckId)
                ankiActivity.showDialogFragment(d)
            }
            DeckPickerContextMenuOption.CREATE_SHORTCUT -> {
                Timber.i("Create icon for a deck")
                (activity as DeckPicker?)!!.createIcon(context, deckId)
            }
            DeckPickerContextMenuOption.RENAME_DECK -> {
                Timber.i("Rename deck selected")
                (activity as DeckPicker?)!!.renameDeckDialog(deckId)
            }
            DeckPickerContextMenuOption.EXPORT_DECK -> {
                Timber.i("Export deck selected")
                (activity as DeckPicker?)!!.exportDeck(deckId)
            }
            DeckPickerContextMenuOption.UNBURY -> {
                Timber.i("Unbury deck selected")
                collection.sched.unburyCardsForDeck(deckId)
                (activity as StudyOptionsListener?)!!.onRequireDeckListUpdate()
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD -> {
                Timber.i("Empty deck selected")
                (activity as DeckPicker?)!!.rebuildFiltered(deckId)
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY -> {
                Timber.i("Empty deck selected")
                (activity as DeckPicker?)!!.emptyFiltered(deckId)
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CREATE_SUBDECK -> {
                Timber.i("Create Subdeck selected")
                (activity as DeckPicker?)!!.createSubDeckDialog(deckId)
            }
            DeckPickerContextMenuOption.BROWSE_CARDS -> {
                collection.decks?.select(deckId)
                val intent = Intent(activity, CardBrowser::class.java)
                (activity as DeckPicker?)!!.startActivityForResultWithAnimation(intent, NavigationDrawerActivity.REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.Direction.START)
            }
        }
    }

    private enum class DeckPickerContextMenuOption(val itemId: Int, @StringRes val optionName: Int) {
        RENAME_DECK(0, R.string.rename_deck),
        DECK_OPTIONS(1, R.string.menu__deck_options),
        CUSTOM_STUDY(2, R.string.custom_study),
        DELETE_DECK(3, R.string.contextmenu_deckpicker_delete_deck),
        EXPORT_DECK(4, R.string.export_deck),
        UNBURY(5, R.string.unbury),
        CUSTOM_STUDY_REBUILD(6, R.string.rebuild_cram_label),
        CUSTOM_STUDY_EMPTY(7, R.string.empty_cram_label),
        CREATE_SUBDECK(8, R.string.create_subdeck),
        CREATE_SHORTCUT(9, R.string.create_shortcut),
        BROWSE_CARDS(10, R.string.browse_cards);

        companion object {
            fun fromId(targetId: Int): DeckPickerContextMenuOption {
                return values().first { it.itemId == targetId }
            }
        }
    }

    class Factory(val collectionSupplier: Supplier<Collection>) : ExtendedFragmentFactory() {
        override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
            val cls = loadFragmentClass(classLoader, className)
            return if (cls == DeckPickerContextMenu::class.java) {
                newDeckPickerContextMenu()
            } else super.instantiate(classLoader, className)
        }

        private fun newDeckPickerContextMenu(): DeckPickerContextMenu =
            DeckPickerContextMenu(collectionSupplier.get())

        fun newDeckPickerContextMenu(deckId: Long): DeckPickerContextMenu =
            DeckPickerContextMenu(collectionSupplier.get()).withArguments(deckId)
    }
}
