/*
 * Copyright (c) 2022 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.core.content.ContextCompat
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck.Companion.fromCollection
import com.ichi2.anki.servicelayer.DeckService.shouldShowDefaultDeck
import com.ichi2.anki.widgets.DeckDropDownAdapter
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.utils.FunctionalInterfaces
import com.ichi2.utils.WithFragmentManager
import com.ichi2.utils.toFragmentManager
import timber.log.Timber
import java.util.*

class DeckSpinnerSelection(context: AnkiActivity, collection: Collection, spinner: Spinner, showAllDecks: Boolean, alwaysShowDefault: Boolean) {
    private val mDeckId: Long = 0

    /**
     * All of the decks shown to the user.
     */
    private var mAllDeckIds: ArrayList<Long>? = null

    /**
     * The spinner displayed in the activity.
     * Empty at construction. After initialization, it contains in this order:
     * * "All decks" if mShowAllDecks is true
     * * then it contains all decks from mAllDeckIds.
     */
    private val mSpinner: Spinner
    private val mWithFragmentManager: WithFragmentManager
    private val mContext: Context
    private val mCollection: Collection
    var dropDownDecks: List<Deck?>? = null
        private set
    private var mDeckDropDownAdapter: DeckDropDownAdapter? = null
    private val mShowAllDecks: Boolean

    /** Whether to show the default deck if it is not visible in the Deck Picker  */
    private val mAlwaysShowDefault: Boolean
    fun initializeActionBarDeckSpinner(actionBar: ActionBar) {
        actionBar.setDisplayShowTitleEnabled(false)

        // Add drop-down menu to select deck to action bar.
        dropDownDecks = computeDropDownDecks()
        mAllDeckIds = ArrayList(dropDownDecks!!.size)
        for (d in dropDownDecks!!) {
            val thisDid = d!!.getLong("id")
            mAllDeckIds!!.add(thisDid)
        }
        mDeckDropDownAdapter = DeckDropDownAdapter(mContext, dropDownDecks)
        mSpinner.adapter = mDeckDropDownAdapter
        setSpinnerListener()
    }

    fun initializeNoteEditorDeckSpinner(currentEditedCard: Card?, addNote: Boolean) {
        val col = mCollection
        dropDownDecks = computeDropDownDecks()
        val deckNames = ArrayList<String>(dropDownDecks!!.size)
        mAllDeckIds = ArrayList(dropDownDecks!!.size)
        for (d in dropDownDecks!!) {
            // add current deck and all other non-filtered decks to deck list
            val thisDid = d!!.getLong("id")
            val currentName = d.getString("name")
            val lineContent: String = if (d.isStd) {
                currentName
            } else {
                // We do not allow cards to be moved to dynamic deck.
                // That mean we do not list dynamic decks in the spinner, with one exception
                if (!addNote && currentEditedCard != null && currentEditedCard.did == thisDid) {
                    // If the current card is in a dynamic deck, it can stay there. Hence current deck is added
                    // to the spinner, even if it is dynamic.
                    mContext.applicationContext.getString(R.string.current_and_default_deck, currentName, col.decks.name(currentEditedCard.oDid))
                } else {
                    continue
                }
            }
            mAllDeckIds!!.add(thisDid)
            deckNames.add(lineContent)
        }
        val noteDeckAdapter: ArrayAdapter<String?> = object : ArrayAdapter<String?>(mContext, R.layout.multiline_spinner_item, deckNames as List<String?>) {
            override fun getDropDownView(position: Int, convertView: View, parent: ViewGroup): View {

                // Cast the drop down items (popup items) as text view
                val tv = super.getDropDownView(position, convertView, parent) as TextView

                // If this item is selected
                if (position == mSpinner.selectedItemPosition) {
                    tv.setBackgroundColor(ContextCompat.getColor(mContext, R.color.note_editor_selected_item_background))
                    tv.setTextColor(ContextCompat.getColor(mContext, R.color.note_editor_selected_item_text))
                }

                // Return the modified view
                return tv
            }
        }
        mSpinner.adapter = noteDeckAdapter
        setSpinnerListener()
    }

    /**
     * @return All decks, except maybe default if it should be hidden.
     */
    protected fun computeDropDownDecks(): List<Deck?> {
        val decks = mCollection.decks.allSorted().toMutableList()
        if (shouldHideDefaultDeck()) {
            decks.removeIf { x: Deck? -> x?.getLong("id") == Consts.DEFAULT_DECK_ID }
        }
        return decks
    }

    fun setSpinnerListener() {
        mSpinner.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                displayDeckOverrideDialog(mCollection)
            }
            true
        }
        setSpinnerVisibility(View.VISIBLE)
    }

    /**
     * Move the selected deck in the spinner to mDeckId.
     * Timber if mDeckId is not an id of a known deck.
     */
    fun updateDeckPosition(deckId: Long) {
        val position = mAllDeckIds!!.indexOf(deckId)
        if (position != -1) {
            mSpinner.setSelection(position)
        } else {
            Timber.e("updateDeckPosition() error :: mCurrentDid=%d, position=%d", mDeckId, position)
        }
    }

    fun notifyDataSetChanged() {
        mDeckDropDownAdapter!!.notifyDataSetChanged()
    }

    fun setEnabledActionBarSpinner(enabled: Boolean) {
        mSpinner.isEnabled = enabled
    }

    fun setSpinnerVisibility(view: Int) {
        mSpinner.visibility = view
    }

    fun hasSpinner(): Boolean {
        return true // Condition 'mSpinner != null' is always 'true'
    }

    /**
     * Iterates the drop down decks, and selects the one matching the given id.
     * @param deckId The deck id to be selected.
     * @param setAsCurrentDeck If true, deckId will be set as the current deck id of Collection
     * (this means the deck selected here will continue to appear in any future Activity whose
     * display data is loaded from Collection's current deck). If false, deckId will not be set as
     * the current deck id of Collection.
     * @return True if selection succeeded.
     */
    fun selectDeckById(deckId: Long, setAsCurrentDeck: Boolean): Boolean {
        return if (deckId == ALL_DECKS_ID) {
            selectAllDecks()
        } else selectDeck(deckId, setAsCurrentDeck)
    }

    /**
     * select in the spinner deck with id
     * @param deckId The deck id to search (not ALL_DECKS_ID)
     * @param setAsCurrentDeck whether this deck should be selected in the collection (if it exists)
     * @return whether it was found
     */
    private fun selectDeck(deckId: Long, setAsCurrentDeck: Boolean): Boolean {
        for (dropDownDeckIdx in mAllDeckIds!!.indices) {
            if (mAllDeckIds!![dropDownDeckIdx] == deckId) {
                val position = if (mShowAllDecks) dropDownDeckIdx + 1 else dropDownDeckIdx
                mSpinner.setSelection(position)
                if (setAsCurrentDeck) {
                    mCollection.decks.select(deckId)
                }
                return true
            }
        }
        return false
    }

    /**
     * Select all decks. Must be called only if mShowAllDecks.
     * @return whether selection was a success.
     */
    fun selectAllDecks(): Boolean {
        if (!mShowAllDecks) {
            AnkiDroidApp.sendExceptionReport("selectAllDecks was called while `mShowAllDecks is false`", "DeckSpinnerSelection:selectAllDecks")
            return false
        }
        mSpinner.setSelection(0)
        return true
    }

    fun displayDeckOverrideDialog(col: Collection?) {
        val nonDynamic = FunctionalInterfaces.Filter { d: Deck? -> !Decks.isDynamic(d) }
        val decks = fromCollection(col!!, nonDynamic).toMutableList()
        if (mShowAllDecks) {
            decks.add(SelectableDeck(ALL_DECKS_ID, mContext.resources.getString(R.string.card_browser_all_decks)))
        }
        if (shouldHideDefaultDeck()) {
            decks.removeIf { x: SelectableDeck -> x.deckId == Consts.DEFAULT_DECK_ID }
        }
        val dialog = newInstance(mContext.getString(R.string.search_deck), null, false, decks)
        AnkiActivity.showDialogFragment(mWithFragmentManager.getFragmentManager(), dialog)
    }

    /**
     * @return Whether default deck should appear in the list of deck
     */
    protected fun shouldHideDefaultDeck(): Boolean {
        return !mAlwaysShowDefault && !shouldShowDefaultDeck(mCollection)
    }

    companion object {
        private const val ALL_DECKS_ID = 0L
    }

    /**
     * @param spinner Currently empty Spinner. Used to access the Android view.
     */
    init {
        mContext = context
        mCollection = collection
        mSpinner = spinner
        mWithFragmentManager = context.toFragmentManager()
        mShowAllDecks = showAllDecks
        mAlwaysShowDefault = alwaysShowDefault
    }
}
