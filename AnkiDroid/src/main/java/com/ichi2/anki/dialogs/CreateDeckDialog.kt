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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.Decks
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.getOrCreateFilteredDeck
import com.ichi2.utils.asLocalizedMessage
import com.ichi2.utils.displayKeyboard
import timber.log.Timber
import java.util.function.Consumer

// TODO: Use snackbars instead of toasts: https://github.com/ankidroid/Anki-Android/pull/12139#issuecomment-1224963182
@NeedsTest("Ensure a toast is shown on a successful action")
class CreateDeckDialog(
    private val context: Context,
    private val title: Int,
    private val deckDialogType: DeckDialogType,
    private val parentId: Long?
) {
    var createDeckSnack: Snackbar? = null
    private var mPreviousDeckName: String? = null
    private var mOnNewDeckCreated: Consumer<Long>? = null
    private var mInitialDeckName = ""
    private var mShownDialog: MaterialDialog? = null

    enum class DeckDialogType {
        FILTERED_DECK, DECK, SUB_DECK, RENAME_DECK
    }

    private val col
        get() = CollectionHelper.instance.getColUnsafe(context)!!

    suspend fun showFilteredDeckDialog() {
        Timber.i("CreateDeckDialog::showFilteredDeckDialog")
        mInitialDeckName = withCol {
            getOrCreateFilteredDeck(did = 0).name
        }
        showDialog()
    }

    /** Used for rename  */
    var deckName: String
        get() = mShownDialog!!.getInputField().text.toString()
        set(deckName) {
            mPreviousDeckName = deckName
            mInitialDeckName = deckName
        }

    fun showDialog(): MaterialDialog {
        @SuppressLint("CheckResult")
        val dialog = MaterialDialog(context).show {
            title(title)
            positiveButton(R.string.dialog_ok) {
                onPositiveButtonClicked()
            }
            negativeButton(R.string.dialog_cancel)
            input(prefill = mInitialDeckName, waitForPositiveButton = false) { dialog, text ->
                // we need the fully-qualified name for subdecks
                val fullyQualifiedDeckName = fullyQualifyDeckName(dialogText = text)
                // if the name is empty, it seems distracting to show an error
                if (!Decks.isValidDeckName(fullyQualifiedDeckName)) {
                    dialog.setActionButtonEnabled(WhichButton.POSITIVE, false)
                    return@input
                }
                dialog.setActionButtonEnabled(WhichButton.POSITIVE, true)
            }
            displayKeyboard(getInputField())
        }
        mShownDialog = dialog
        return dialog
    }

    /**
     * Returns the fully qualified deck name for the provided input
     * @param dialogText The user supplied text in the dialog
     * @return [dialogText], or the deck name containing `::` in case of [DeckDialogType.SUB_DECK]
     */
    private fun fullyQualifyDeckName(dialogText: CharSequence) =
        when (deckDialogType) {
            DeckDialogType.DECK, DeckDialogType.FILTERED_DECK, DeckDialogType.RENAME_DECK -> dialogText.toString()
            DeckDialogType.SUB_DECK -> col.decks.getSubdeckName(parentId!!, dialogText.toString())
        }

    fun closeDialog() {
        mShownDialog?.dismiss()
    }

    fun createSubDeck(did: DeckId, deckName: String?) {
        val deckNameWithParentName = col.decks.getSubdeckName(did, deckName)
        createDeck(deckNameWithParentName!!)
    }

    fun createDeck(deckName: String) {
        if (Decks.isValidDeckName(deckName)) {
            createNewDeck(deckName)
            // 11668: Display feedback if a deck is created
            displayFeedback(context.getString(R.string.deck_created))
        } else {
            Timber.d("CreateDeckDialog::createDeck - Not creating invalid deck name '%s'", deckName)
            displayFeedback(context.getString(R.string.invalid_deck_name), Snackbar.LENGTH_LONG)
        }
        closeDialog()
    }

    fun createFilteredDeck(deckName: String): Boolean {
        try {
            // create filtered deck
            Timber.i("CreateDeckDialog::createFilteredDeck...")
            val newDeckId = col.decks.newDyn(deckName)
            mOnNewDeckCreated!!.accept(newDeckId)
        } catch (ex: DeckRenameException) {
            displayFeedback(ex.asLocalizedMessage(context), Snackbar.LENGTH_LONG)
            return false
        }
        return true
    }

    private fun createNewDeck(deckName: String): Boolean {
        try {
            // create normal deck or sub deck
            Timber.i("CreateDeckDialog::createNewDeck")
            val newDeckId = col.decks.id(deckName)
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
            displayFeedback(context.getString(R.string.invalid_deck_name), Snackbar.LENGTH_LONG)
        } else if (newName != mPreviousDeckName) {
            try {
                val decks = col.decks
                val deckId = decks.id(mPreviousDeckName!!)
                decks.rename(decks.get(deckId)!!, newName)
                mOnNewDeckCreated!!.accept(deckId)
                // 11668: Display feedback if a deck is renamed
                displayFeedback(context.getString(R.string.deck_renamed))
            } catch (e: DeckRenameException) {
                Timber.w(e)
                // We get a localized string from libanki to explain the error
                displayFeedback(e.asLocalizedMessage(context), Snackbar.LENGTH_LONG)
            }
        }
    }

    private fun displayFeedback(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        if (context is Activity) {
            context.showSnackbar(message, duration) {
                if (context is DeckPicker) {
                    createDeckSnack = this
                }
            }
        } else {
            showThemedToast(context, message, duration == Snackbar.LENGTH_SHORT)
        }
    }

    fun setOnNewDeckCreated(c: Consumer<Long>?) {
        mOnNewDeckCreated = c
    }
}
