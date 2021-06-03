/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2></perceptualchaos2>@gmail.com>                          *
 * *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 * *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 * *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                           *
 */
package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import androidx.annotation.IntDef
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.libanki.Collection
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.util.*

@SuppressLint("ConstantFieldName")
class DeckPickerContextMenu : AnalyticsDialogFragment() {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(CONTEXT_MENU_RENAME_DECK, CONTEXT_MENU_DECK_OPTIONS, CONTEXT_MENU_CUSTOM_STUDY, CONTEXT_MENU_DELETE_DECK, CONTEXT_MENU_EXPORT_DECK, CONTEXT_MENU_UNBURY, CONTEXT_MENU_CUSTOM_STUDY_REBUILD, CONTEXT_MENU_CUSTOM_STUDY_EMPTY, CONTEXT_MENU_CREATE_SUBDECK, CONTEXT_MENU_CREATE_SHORTCUT)
    annotation class DECK_PICKER_CONTEXT_MENU

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val did: Long? = getArguments()?.getLong("did")
        val title: String = did?.let { CollectionHelper.getInstance().getCol(getContext()).getDecks().name(it) }.toString()
        val itemIds = listIds
        return requireActivity().let {
            MaterialDialog.Builder(it)
                .title(title)
                .cancelable(true)
                .autoDismiss(false)
                .itemsIds(itemIds)
                .items(*ContextMenuHelper.getValuesFromKeys(keyValueMap, itemIds))
                .itemsCallback(mContextMenuListener)
                .build()
        }!!
    }

    private val keyValueMap: HashMap<Int, String>
        private get() {
            val res: Resources = getResources()
            val keyValueMap = HashMap<Int, String>(9)
            keyValueMap[CONTEXT_MENU_RENAME_DECK] = res.getString(R.string.rename_deck)
            keyValueMap[CONTEXT_MENU_DECK_OPTIONS] = res.getString(R.string.menu__deck_options)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY] = res.getString(R.string.custom_study)
            keyValueMap[CONTEXT_MENU_DELETE_DECK] = res.getString(R.string.contextmenu_deckpicker_delete_deck)
            keyValueMap[CONTEXT_MENU_EXPORT_DECK] = res.getString(R.string.export_deck)
            keyValueMap[CONTEXT_MENU_UNBURY] = res.getString(R.string.unbury)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY_REBUILD] = res.getString(R.string.rebuild_cram_label)
            keyValueMap[CONTEXT_MENU_CUSTOM_STUDY_EMPTY] = res.getString(R.string.empty_cram_label)
            keyValueMap[CONTEXT_MENU_CREATE_SUBDECK] = res.getString(R.string.create_subdeck)
            keyValueMap[CONTEXT_MENU_CREATE_SHORTCUT] = res.getString(R.string.create_shortcut)
            return keyValueMap
        }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @return the ids of which values to show
     */
    @get:DECK_PICKER_CONTEXT_MENU
    private val listIds: IntArray
        private get() {
            val col: Collection = CollectionHelper.getInstance().getCol(getContext())
            val did: Long? = getArguments()?.getLong("did")
            val dyn = did?.let { col.decks.isDyn(it) }
            val itemIds = ArrayList<Int>(9)
            if (dyn == true) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_REBUILD)
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY_EMPTY)
            }
            itemIds.add(CONTEXT_MENU_RENAME_DECK)
            if (dyn == false) {
                itemIds.add(CONTEXT_MENU_CREATE_SUBDECK)
            }
            itemIds.add(CONTEXT_MENU_DECK_OPTIONS)
            if (dyn == false) {
                itemIds.add(CONTEXT_MENU_CUSTOM_STUDY)
            }
            itemIds.add(CONTEXT_MENU_DELETE_DECK)
            itemIds.add(CONTEXT_MENU_EXPORT_DECK)
            if (did?.let { col.sched.haveBuried(it) } == true) {
                itemIds.add(CONTEXT_MENU_UNBURY)
            }
            itemIds.add(CONTEXT_MENU_CREATE_SHORTCUT)
            return ContextMenuHelper.integerListToArray(itemIds)
        }

    // Handle item selection on context menu which is shown when the user long-clicks on a deck
    private val mContextMenuListener: MaterialDialog.ListCallback = MaterialDialog.ListCallback { materialDialog: MaterialDialog?, view: View, item: Int, charSequence: CharSequence? ->
        @DECK_PICKER_CONTEXT_MENU val id = view.id
        when (id) {
            CONTEXT_MENU_DELETE_DECK -> {
                Timber.i("Delete deck selected")
                (getActivity() as DeckPicker).confirmDeckDeletion()
            }
            CONTEXT_MENU_DECK_OPTIONS -> {
                Timber.i("Open deck options selected")
                (getActivity() as DeckPicker).showContextMenuDeckOptions()
                (getActivity() as AnkiActivity).dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY -> {
                Timber.i("Custom study option selected")
                val did: Long? = getArguments()?.getLong("did")
                val ankiActivity: AnkiActivity = requireActivity() as AnkiActivity
                val d: CustomStudyDialog = FragmentFactoryUtils.instantiate<CustomStudyDialog>(ankiActivity, CustomStudyDialog::class.java)
                did?.let { d.withArguments(CustomStudyDialog.CONTEXT_MENU_STANDARD, it) }
                ankiActivity.showDialogFragment(d)
            }
            CONTEXT_MENU_CREATE_SHORTCUT -> {
                Timber.i("Create icon for a deck")
                (getActivity() as DeckPicker).createIcon(getContext())
            }
            CONTEXT_MENU_RENAME_DECK -> {
                Timber.i("Rename deck selected")
                (getActivity() as DeckPicker).renameDeckDialog()
            }
            CONTEXT_MENU_EXPORT_DECK -> {
                Timber.i("Export deck selected")
                (getActivity() as DeckPicker).showContextMenuExportDialog()
            }
            CONTEXT_MENU_UNBURY -> {
                Timber.i("Unbury deck selected")
                val col: Collection = CollectionHelper.getInstance().getCol(getContext())
                getArguments()?.getLong("did")?.let { col.sched.unburyCardsForDeck(it) }
                (getActivity() as StudyOptionsFragment.StudyOptionsListener).onRequireDeckListUpdate()
                (getActivity() as AnkiActivity).dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY_REBUILD -> {
                Timber.i("Empty deck selected")
                (getActivity() as DeckPicker).rebuildFiltered()
                (getActivity() as AnkiActivity).dismissAllDialogFragments()
            }
            CONTEXT_MENU_CUSTOM_STUDY_EMPTY -> {
                Timber.i("Empty deck selected")
                (getActivity() as DeckPicker).emptyFiltered()
                (getActivity() as AnkiActivity).dismissAllDialogFragments()
            }
            CONTEXT_MENU_CREATE_SUBDECK -> {
                Timber.i("Create Subdeck selected")
                (getActivity() as DeckPicker).createSubdeckDialog()
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
        fun newInstance(did: Long): DeckPickerContextMenu {
            val f = DeckPickerContextMenu()
            val args = Bundle()
            args.putLong("did", did)
            f.setArguments(args)
            return f
        }
    }
}
