/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
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

import android.content.Context
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.MaterialEditTextDialog.Companion.displayKeyboard
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.libanki.Decks
import com.ichi2.libanki.backend.exception.DeckRenameException
import timber.log.Timber
import java.util.function.Consumer

class CreateDeckDialog(private val context: Context, private val title: Int, private val deckDialogType: DeckDialogType, private val parentId: Long?) {
    private var mPreviousDeckName: String? = null
    private var mOnNewDeckCreated: Consumer<Long>? = null
    private var mInitialDeckName = ""
    private var mShownDialog: MaterialDialog? = null

    enum class DeckDialogType {
        FILTERED_DECK, DECK, SUB_DECK, RENAME_DECK
    }

    fun showFilteredDeckDialog() {
        Timber.i("CreateDeckDialog::showFilteredDeckDialog")
        val names = CollectionHelper.getInstance().getCol(context).decks.allNames()
        var n = 1
        val namePrefix = context.resources.getString(R.string.filtered_deck_name) + " "
        while (names.contains(namePrefix + n)) {
            n++
        }
        mInitialDeckName = namePrefix + n
        showDialog()
    }

    /** Used for rename  */
    var deckName: String
        get() = mShownDialog!!.inputEditText!!.text.toString()
        set(deckName) {
            mPreviousDeckName = deckName
            mInitialDeckName = deckName
        }

    fun showDialog(): MaterialDialog {
        val show = MaterialDialog.Builder(context).title(title)
            .positiveText(R.string.dialog_ok)
            .negativeText(R.string.dialog_cancel)
            .input(null, mInitialDeckName) { _: MaterialDialog?, _: CharSequence? -> }
            .inputRange(1, -1)
            .onPositive { _: MaterialDialog?, _: DialogAction? -> onPositiveButtonClicked() }
            .show()
        displayKeyboard(show.inputEditText!!, show)
        mShownDialog = show
        return show
    }

    fun closeDialog() {
        if (mShownDialog == null) {
            return
        }
        mShownDialog!!.dismiss()
    }

    fun createSubDeck(did: Long, deckName: String?) {
        val deckNameWithParentName = CollectionHelper.getInstance().getCol(context).decks.getSubdeckName(did, deckName)
        createDeck(deckNameWithParentName!!)
    }

    fun createDeck(deckName: String) {
        if (Decks.isValidDeckName(deckName)) {
            createNewDeck(deckName)
        } else {
            Timber.d("CreateDeckDialog::createDeck - Not creating invalid deck name '%s'", deckName)
            showThemedToast(context, context.getString(R.string.invalid_deck_name), false)
        }
        closeDialog()
    }

    fun createFilteredDeck(deckName: String): Boolean {
        try {
            // create filtered deck
            Timber.i("CreateDeckDialog::createFilteredDeck...")
            val newDeckId = CollectionHelper.getInstance().getCol(context).decks.newDyn(deckName)
            mOnNewDeckCreated!!.accept(newDeckId)
        } catch (ex: DeckRenameException) {
            showThemedToast(context, ex.getLocalizedMessage(context.resources), false)
            return false
        }
        return true
    }

    private fun createNewDeck(deckName: String): Boolean {
        try {
            // create normal deck or sub deck
            Timber.i("CreateDeckDialog::createNewDeck")
            val newDeckId = CollectionHelper.getInstance().getCol(context).decks.id(deckName)
            mOnNewDeckCreated!!.accept(newDeckId)
        } catch (filteredAncestor: DeckRenameException) {
            Timber.w(filteredAncestor)
            return false
        }
        return true
    }

    private fun onPositiveButtonClicked() {
        if (deckName.isNotEmpty()) {
            when (deckDialogType) {
                DeckDialogType.DECK -> {

                    // create deck
                    createDeck(deckName)
                }
                DeckDialogType.RENAME_DECK -> {
                    renameDeck(deckName)
                }
                DeckDialogType.SUB_DECK -> {

                    // create sub deck
                    createSubDeck(parentId!!, deckName)
                }
                DeckDialogType.FILTERED_DECK -> {

                    // create filtered deck
                    createFilteredDeck(deckName)
                }
            }
        }
    }

    fun renameDeck(newDeckName: String) {
        val newName = newDeckName.replace("\"".toRegex(), "")
        if (!Decks.isValidDeckName(newName)) {
            Timber.i("CreateDeckDialog::renameDeck not renaming deck to invalid name '%s'", newName)
            showThemedToast(context, context.getString(R.string.invalid_deck_name), false)
        } else if (newName != mPreviousDeckName) {
            try {
                val col = CollectionHelper.getInstance().getCol(context)
                val deckId = col.decks.id(mPreviousDeckName!!)
                col.decks.rename(col.decks.get(deckId), newName)
                mOnNewDeckCreated!!.accept(deckId)
            } catch (e: DeckRenameException) {
                Timber.w(e)
                // We get a localized string from libanki to explain the error
                showThemedToast(context, e.getLocalizedMessage(context.resources), false)
            }
        }
    }

    fun setOnNewDeckCreated(c: Consumer<Long>?) {
        mOnNewDeckCreated = c
    }
}
