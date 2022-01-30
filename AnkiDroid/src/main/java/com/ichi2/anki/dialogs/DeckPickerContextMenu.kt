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
import androidx.annotation.IntDef
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.*
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.libanki.Collection
import com.ichi2.utils.ExtendedFragmentFactory
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import java.util.function.Supplier

class DeckPickerContextMenu(private val collection: Collection) : AnalyticsDialogFragment() {
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(CONTEXT_MENU_RENAME_DECK, CONTEXT_MENU_DECK_OPTIONS, CONTEXT_MENU_CUSTOM_STUDY, CONTEXT_MENU_DELETE_DECK, CONTEXT_MENU_EXPORT_DECK, CONTEXT_MENU_UNBURY, CONTEXT_MENU_CUSTOM_STUDY_REBUILD, CONTEXT_MENU_CUSTOM_STUDY_EMPTY, CONTEXT_MENU_CREATE_SUBDECK, CONTEXT_MENU_CREATE_SHORTCUT, CONTEXT_MENU_BROWSE_CARDS)
    annotation class DECK_PICKER_CONTEXT_MENU

    fun withArguments(did: Long): DeckPickerContextMenu {
        val args = this.arguments ?: Bundle()
        args.putLong("did", did)
        this.arguments = args
        return this
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val did = requireArguments().getLong("did")
        val title = collection.decks.name(did)
        val itemIds = listIds
        return MaterialDialog.Builder(requireActivity())
            .title(title)
            .cancelable(true)
            .autoDismiss(false)
            .itemsIds(itemIds)
            .items(*ContextMenuHelper.getValuesFromKeys(keyValueMap, itemIds))
            .itemsCallback(mContextMenuListener)
            .build()
    }

    /**
     * A map from an ID of a menu item to its name
     *
     * note: ordering of menu items is performed in [listIds]
     * */
    private val keyValueMap: HashMap<Int, String>
        get() {
            val res = resources
            return hashMapOf(
                CONTEXT_MENU_RENAME_DECK to res.getString(R.string.rename_deck),
                CONTEXT_MENU_DECK_OPTIONS to res.getString(R.string.menu__deck_options),
                CONTEXT_MENU_CUSTOM_STUDY to res.getString(R.string.custom_study),
                CONTEXT_MENU_DELETE_DECK to res.getString(R.string.contextmenu_deckpicker_delete_deck),
                CONTEXT_MENU_EXPORT_DECK to res.getString(R.string.export_deck),
                CONTEXT_MENU_UNBURY to res.getString(R.string.unbury),
                CONTEXT_MENU_CUSTOM_STUDY_REBUILD to res.getString(R.string.rebuild_cram_label),
                CONTEXT_MENU_CUSTOM_STUDY_EMPTY to res.getString(R.string.empty_cram_label),
                CONTEXT_MENU_CREATE_SUBDECK to res.getString(R.string.create_subdeck),
                CONTEXT_MENU_CREATE_SHORTCUT to res.getString(R.string.create_shortcut),
                CONTEXT_MENU_BROWSE_CARDS to res.getString(R.string.browse_cards),
            )
        }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @return the ids of which values to show
     */
    @get:DECK_PICKER_CONTEXT_MENU
    private val listIds: IntArray
        get() {
            val did = requireArguments().getLong("did")
            val dyn = collection.decks.isDyn(did)
            val itemIds = ArrayList<Int>(11) // init with our fixed list size for performance
            itemIds.add(CONTEXT_MENU_BROWSE_CARDS)
            if (dyn) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_REBUILD)
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_EMPTY)
            }
            itemIds.add(CONTEXT_MENU_RENAME_DECK)
            if (!dyn) {
                itemIds.add(CONTEXT_MENU_CREATE_SUBDECK)
            }
            itemIds.add(CONTEXT_MENU_DECK_OPTIONS)
            if (!dyn) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY)
            }
            itemIds.add(CONTEXT_MENU_EXPORT_DECK)
            if (collection.sched.haveBuried(did)) {
                itemIds.add(CONTEXT_MENU_UNBURY)
            }
            itemIds.add(CONTEXT_MENU_CREATE_SHORTCUT)
            itemIds.add(CONTEXT_MENU_DELETE_DECK)
            return ContextMenuHelper.integerListToArray(itemIds)
        }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private val mContextMenuListener = ListCallback { _: MaterialDialog?, view: View, _: Int, _: CharSequence? ->
        when (view.id) {
            CONTEXT_MENU_DELETE_DECK -> {
                Timber.i("Delete deck selected")
                (activity as DeckPicker?)!!.confirmDeckDeletion()
            }
            CONTEXT_MENU_DECK_OPTIONS -> {
                Timber.i("Open deck options selected")
                (activity as DeckPicker?)!!.showContextMenuDeckOptions()
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY -> {
                Timber.i("Custom study option selected")
                val did = requireArguments().getLong("did")
                val ankiActivity = requireActivity() as AnkiActivity
                val d = FragmentFactoryUtils.instantiate(ankiActivity, CustomStudyDialog::class.java)
                d.withArguments(CustomStudyDialog.CONTEXT_MENU_STANDARD, did)
                ankiActivity.showDialogFragment(d)
            }
            CONTEXT_MENU_CREATE_SHORTCUT -> {
                Timber.i("Create icon for a deck")
                (activity as DeckPicker?)!!.createIcon(context)
            }
            CONTEXT_MENU_RENAME_DECK -> {
                Timber.i("Rename deck selected")
                (activity as DeckPicker?)!!.renameDeckDialog()
            }
            CONTEXT_MENU_EXPORT_DECK -> {
                Timber.i("Export deck selected")
                (activity as DeckPicker?)!!.showContextMenuExportDialog()
            }
            CONTEXT_MENU_UNBURY -> {
                Timber.i("Unbury deck selected")
                collection.sched.unburyCardsForDeck(requireArguments().getLong("did"))
                (activity as StudyOptionsListener?)!!.onRequireDeckListUpdate()
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY_REBUILD -> {
                Timber.i("Empty deck selected")
                (activity as DeckPicker?)!!.rebuildFiltered()
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY_EMPTY -> {
                Timber.i("Empty deck selected")
                (activity as DeckPicker?)!!.emptyFiltered()
                (activity as AnkiActivity?)!!.dismissAllDialogFragments()
            }
            CONTEXT_MENU_CREATE_SUBDECK -> {
                Timber.i("Create Subdeck selected")
                (activity as DeckPicker?)!!.createSubdeckDialog()
            }
            CONTEXT_MENU_BROWSE_CARDS -> {
                val did = requireArguments().getLong("did")
                collection.decks?.select(did)
                val intent = Intent(activity, CardBrowser::class.java)
                (activity as DeckPicker?)!!.startActivityForResultWithAnimation(intent, NavigationDrawerActivity.REQUEST_BROWSE_CARDS, ActivityTransitionAnimation.Direction.START)
            }
        }
    }

    companion object {
        /**
         * Context Menus
         */
        private const val CONTEXT_MENU_RENAME_DECK = 0
        private const val CONTEXT_MENU_DECK_OPTIONS = 1
        private const val CONTEXT_MENU_CUSTOM_STUDY = 2
        private const val CONTEXT_MENU_DELETE_DECK = 3
        private const val CONTEXT_MENU_EXPORT_DECK = 4
        private const val CONTEXT_MENU_UNBURY = 5
        private const val CONTEXT_MENU_CUSTOM_STUDY_REBUILD = 6
        private const val CONTEXT_MENU_CUSTOM_STUDY_EMPTY = 7
        private const val CONTEXT_MENU_CREATE_SUBDECK = 8
        private const val CONTEXT_MENU_CREATE_SHORTCUT = 9
        private const val CONTEXT_MENU_BROWSE_CARDS = 10
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
