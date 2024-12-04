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

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckCreationListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck.Companion.fromCollection
import com.ichi2.anki.utils.showDialogFragmentImpl
import com.ichi2.anki.widgets.DeckDropDownAdapter
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.DeckNameId
import com.ichi2.utils.FragmentManagerSupplier
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.asFragmentManagerSupplier
import timber.log.Timber

/**
 * Handles expansion of a [Spinner], allowing a press to display a [DeckSelectionDialog]
 * Responsible for filtering the decks to display in the [DeckSelectionDialog]
 *
 * Populates the spinner with data, and handles display of the selected deck within the spinner control
 *
 * @param spinner
 * The spinner displayed in the activity.
 * Empty at construction. After initialization, it contains in this order:
 * 1. "All decks" if [showAllDecks] is true
 * 2. All decks from [dropDownDecks].
 * @param showAllDecks Whether the deck selection should allow "All Decks" as an option
 * @param alwaysShowDefault If true, never hide the default deck. If false, match [DeckPicker]'s logic
 * @param showFilteredDecks whether to show filtered decks
 */
@KotlinCleanup(
    "this class is a mess: showAllDecks, AND the adapter seems overly complicated as " +
        "only the selected item is visible"
)
class DeckSpinnerSelection(
    private val context: AppCompatActivity,
    private val spinner: Spinner,
    private val showAllDecks: Boolean,
    private val alwaysShowDefault: Boolean,
    private val showFilteredDecks: Boolean,
    private val fragmentManagerSupplier: FragmentManagerSupplier = context.asFragmentManagerSupplier()
) {

    private var deckDropDownAdapter: DeckDropDownAdapter? = null

    // This should be deckDropDownAdapter.decks
    // but this class also handles initializeNoteEditorDeckSpinner, so this can't happen yet
    private var dropDownDecks: MutableList<DeckNameId>? = null

    @MainThread // spinner.adapter
    fun initializeActionBarDeckSpinner(col: Collection, actionBar: ActionBar) {
        actionBar.setDisplayShowTitleEnabled(false)

        // Add drop-down menu to select deck to action bar.
        computeDropDownDecks(col, includeFiltered = showFilteredDecks).toMutableList().let {
            dropDownDecks = it
            deckDropDownAdapter = DeckDropDownAdapter(context, it)
            spinner.adapter = deckDropDownAdapter
            setSpinnerListener()
        }
    }

    @MainThread // spinner.adapter
    suspend fun initializeStatsBarDeckSpinner() {
        withCol {
            decks.allNamesAndIds(includeFiltered = showFilteredDecks, skipEmptyDefault = true)
        }.toMutableList().let {
            dropDownDecks = it
            // custom implementation as DeckDropDownAdapter automatically includes a ALL_DECKS entry +
            // in order for the spinner to wrap the content a row layout with wrap_content for root
            // width was introduced
            spinner.adapter = object : ArrayAdapter<DeckNameId>(
                context,
                R.layout.item_stats_deck,
                it
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val rowView = super.getView(position, convertView, parent)
                    rowView.findViewById<TextView>(R.id.title).text = getItem(position)!!.name
                    return rowView
                }
            }.apply { setDropDownViewResource(android.R.layout.simple_spinner_item) }
            setSpinnerListener()
        }
    }

    @MainThread // spinner.adapter
    fun initializeNoteEditorDeckSpinner(col: Collection, @LayoutRes layoutResource: Int = R.layout.multiline_spinner_item) {
        computeDropDownDecks(col, includeFiltered = false).toMutableList().let {
            dropDownDecks = it
            val deckNames = it.map { it.name }
            val noteDeckAdapter: ArrayAdapter<String?> = object :
                ArrayAdapter<String?>(context, layoutResource, deckNames as List<String?>) {
                override fun getDropDownView(
                    position: Int,
                    convertView: View?,
                    parent: ViewGroup
                ): View {
                    // Cast the drop down items (popup items) as text view
                    val tv = super.getDropDownView(position, convertView, parent) as TextView

                    // If this item is selected
                    if (position == spinner.selectedItemPosition) {
                        tv.setBackgroundColor(context.getColor(R.color.note_editor_selected_item_background))
                        tv.setTextColor(context.getColor(R.color.note_editor_selected_item_text))
                    }

                    // Return the modified view
                    return tv
                }
            }
            spinner.adapter = noteDeckAdapter
            setSpinnerListener()
        }
    }

    /** @return All decks.  */
    suspend fun computeDropDownDecks(includeFiltered: Boolean): List<DeckNameId> =
        withCol { computeDropDownDecks(this, includeFiltered) }

    /** @return All decks. */
    private fun computeDropDownDecks(col: Collection, includeFiltered: Boolean): List<DeckNameId> =
        col.decks.allNamesAndIds(includeFiltered = includeFiltered)

    private fun setSpinnerListener() {
        spinner.setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                context.launchCatchingTask { displayDeckSelectionDialog() }
            }
            true
        }
        setSpinnerVisibility(View.VISIBLE)
    }

    /**
     * Move the selected deck in the spinner to [deckId].
     * Timber if [deckId] is not an id of a known deck.
     * @param deckId The ID of the deck to select
     */
    fun updateDeckPosition(deckId: DeckId) {
        // TODO: This doesn't handle ALL_DECKS
        val position = dropDownDecks?.map { it.id }?.indexOf(deckId) ?: -1
        if (position != -1) {
            spinner.setSelection(position)
        } else {
            Timber.w("updateDeckPosition() error :: deckId=%d, position=%d", deckId, position)
        }
    }

    fun notifyDataSetChanged() {
        deckDropDownAdapter!!.notifyDataSetChanged()
    }

    fun setEnabledActionBarSpinner(enabled: Boolean) {
        spinner.isEnabled = enabled
    }

    fun setSpinnerVisibility(view: Int) {
        spinner.visibility = view
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
    suspend fun selectDeckById(deckId: DeckId, setAsCurrentDeck: Boolean): Boolean {
        return if (deckId == ALL_DECKS_ID || this.dropDownDecks == null) {
            selectAllDecks()
        } else {
            selectDeck(deckId, setAsCurrentDeck)
        }
    }

    /**
     * select in the spinner deck with id
     * @param deckId The deck id to search (not ALL_DECKS_ID)
     * @param setAsCurrentDeck whether this deck should be selected in the collection (if it exists)
     * @return whether it was found
     */
    private suspend fun selectDeck(deckId: DeckId, setAsCurrentDeck: Boolean): Boolean {
        val deck = this.dropDownDecks?.withIndex()?.firstOrNull { it.value.id == deckId } ?: return false
        val position = if (showAllDecks) deck.index + 1 else deck.index
        spinner.setSelection(position)
        if (setAsCurrentDeck) {
            withCol { decks.select(deckId) }
        }
        return true
    }

    /**
     * Select all decks. Must be called only if [showAllDecks].
     * @return whether selection was a success.
     */
    fun selectAllDecks(): Boolean {
        if (!showAllDecks) {
            CrashReportService.sendExceptionReport("selectAllDecks was called while `showAllDecks is false`", "DeckSpinnerSelection:selectAllDecks")
            return false
        }
        spinner.setSelection(0)
        return true
    }

    /**
     * Displays a [DeckSelectionDialog]
     */
    suspend fun displayDeckSelectionDialog() {
        val decks = fromCollection(includeFiltered = showFilteredDecks).toMutableList()
        if (showAllDecks) {
            decks.add(SelectableDeck(ALL_DECKS_ID, context.resources.getString(R.string.card_browser_all_decks)))
        }
        val dialog = DeckSelectionDialog.newInstance(context.getString(R.string.search_deck), null, false, decks)
        // TODO: retain state after onDestroy
        dialog.deckCreationListener = DeckCreationListener { onDeckAdded(it) }
        showDialogFragmentImpl(fragmentManagerSupplier.getFragmentManager(), dialog)
    }

    private fun onDeckAdded(deck: DeckNameId) {
        Timber.d("added deck %s to spinner", deck)
        deckDropDownAdapter?.addDeck(deck)
        dropDownDecks?.add(deck)
    }

    companion object {
        const val ALL_DECKS_ID = 0L
    }
}
