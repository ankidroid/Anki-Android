/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.DeckSelectionDialog.DecksArrayAdapter.DecksFilter
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.showThemedToast
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.*
import com.ichi2.utils.DeckNameComparator
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.TypedFilter
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

/**
 * "Deck Search": A dialog allowing the user to select a deck from a list of decks.
 *
 * * Allows filtering of visible decks based on name (searching): [DecksFilter]
 * * Allows adding a new deck: [showDeckDialog]
 * * Allows adding a subdeck via long-pressing a deck: [showSubDeckDialog]
 *
 * It is opened when the user wants a deck in stats, browser or note editor.
 *
 * @see SelectableDeck The data that is displayed
 */
@NeedsTest("simulate 'don't keep activities'")
open class DeckSelectionDialog : AnalyticsDialogFragment() {
    private var dialog: MaterialDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = LayoutInflater.from(activity)
            .inflate(R.layout.deck_picker_dialog, null, false)
        val summary = dialogView.findViewById<TextView>(R.id.deck_picker_dialog_summary)
        val arguments = requireArguments()
        if (getSummaryMessage(arguments) == null) {
            summary.visibility = View.GONE
        } else {
            summary.visibility = View.VISIBLE
            summary.text = getSummaryMessage(arguments)
        }
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.deck_picker_dialog_list)
        recyclerView.requestFocus()
        val deckLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(requireActivity())
        recyclerView.layoutManager = deckLayoutManager
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
        val decks: List<SelectableDeck> = getDeckNames(arguments)
        val adapter = DecksArrayAdapter(decks)
        recyclerView.adapter = adapter
        adjustToolbar(dialogView, adapter)
        val args = requireArguments()
        if (args.containsKey("currentDeckId")) {
            val did = args.getLong("currentDeckId")
            recyclerView.scrollToPosition(getPositionOfDeck(did, adapter.getCurrentlyDisplayedDecks()))
        }
        // TODO: AlertDialog conversion: [CardBrowser] keyboard appears when searching (#15613)
        dialog = MaterialDialog(requireActivity())
            .negativeButton(R.string.dialog_cancel)
            .customView(view = dialogView, noVerticalPadding = true)
        if (arguments.getBoolean(KEEP_RESTORE_DEFAULT_BUTTON)) {
            (dialog as MaterialDialog).positiveButton(R.string.restore_default) {
                onDeckSelected(null)
            }
        }
        return dialog!!
    }

    private fun getPositionOfDeck(did: DeckId, decks: List<SelectableDeck>) =
        decks.indexOfFirst { it.deckId == did }

    private fun getSummaryMessage(arguments: Bundle): String? {
        return arguments.getString(SUMMARY_MESSAGE)
    }

    private fun getDeckNames(arguments: Bundle): ArrayList<SelectableDeck> =
        BundleCompat.getParcelableArrayList(arguments, DECK_NAMES, SelectableDeck::class.java)!!

    private val title: String
        get() = requireArguments().getString(TITLE)!!

    private fun adjustToolbar(dialogView: View, adapter: DecksArrayAdapter) {
        val toolbar: Toolbar = dialogView.findViewById(R.id.deck_picker_dialog_toolbar)
        toolbar.title = title
        toolbar.inflateMenu(R.menu.deck_picker_dialog_menu)
        val searchItem = toolbar.menu.findItem(R.id.deck_picker_dialog_action_filter)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.deck_picker_dialog_filter_decks)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })
        val addDecks = toolbar.menu.findItem(R.id.deck_picker_dialog_action_add_deck)
        addDecks.setOnMenuItemClickListener {
            // creating new deck without any parent deck
            showDeckDialog()
            true
        }
    }

    private fun showSubDeckDialog(parentDeckPath: String) {
        launchCatchingTask {
            val parentId = withCol { decks.id(parentDeckPath) }
            val createDeckDialog = CreateDeckDialog(requireActivity(), R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, parentId)
            createDeckDialog.onNewDeckCreated = { did: DeckId -> onNewDeckCreated(did) }
            createDeckDialog.showDialog()
        }
    }

    private fun showDeckDialog() {
        val createDeckDialog = CreateDeckDialog(requireActivity(), R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
        createDeckDialog.onNewDeckCreated = { did: DeckId -> onNewDeckCreated(did) }
        createDeckDialog.showDialog()
    }

    /** Updates the list and simulates a click on the newly created deck */
    private fun onNewDeckCreated(id: DeckId) {
        // a deck/subdeck was created
        launchCatchingTask {
            val name = withCol { decks.name(id) }
            val deck = SelectableDeck(id, name)
            deckCreationListener?.onDeckCreated(DeckNameId(name, id))
            selectDeckAndClose(deck)
        }
    }

    /**
     * @param deck deck sent to the listener.
     */
    private fun onDeckSelected(deck: SelectableDeck?) {
        deckSelectionListener!!.onDeckSelected(deck)
    }

    @KotlinCleanup("Use a factory here")
    var deckSelectionListener: DeckSelectionListener? = null
        get() {
            if (field != null) {
                return field
            }
            val activity: Activity = requireActivity()
            if (activity is DeckSelectionListener) {
                return activity
            }
            val parentFragment = parentFragment
            if (parentFragment is DeckSelectionListener) {
                return parentFragment
            }
            throw IllegalStateException("Neither activity or parent fragment were a selection listener")
        }

    var deckCreationListener: DeckCreationListener? = null

    /**
     * Same action as pressing on the deck in the list. I.e. send the deck to listener and close the dialog.
     */
    protected fun selectDeckAndClose(deck: SelectableDeck) {
        Timber.d("selected deck '%s'", deck.name)
        onDeckSelected(deck)
        dialog!!.dismiss()
    }

    protected fun displayErrorAndCancel() {
        dialog!!.dismiss()
    }

    open inner class DecksArrayAdapter(deckNames: List<SelectableDeck>) : RecyclerView.Adapter<DecksArrayAdapter.ViewHolder>(), Filterable {
        inner class ViewHolder(private val deckTextView: TextView) : RecyclerView.ViewHolder(deckTextView) {
            var deckName: String = ""
            private var deckID: Long = -1L

            fun setDeck(deck: SelectableDeck) {
                deckName = deck.name
                deckTextView.text = deck.displayName
                deckID = deck.deckId
            }

            init {
                deckTextView.setOnClickListener {
                    selectDeckByNameAndClose(deckName)
                }
                deckTextView.setOnLongClickListener { // creating sub deck with parent deck path
                    if (deckID == DeckSpinnerSelection.ALL_DECKS_ID) {
                        context?.let { showThemedToast(it, R.string.cannot_create_subdeck_for_all_decks, true) }
                    } else {
                        showSubDeckDialog(deckName)
                    }
                    true
                }
            }
        }

        private val allDecksList = ArrayList<SelectableDeck>()
        private val currentlyDisplayedDecks = ArrayList<SelectableDeck>()
        protected fun selectDeckByNameAndClose(deckName: String) {
            val deck = allDecksList.firstOrNull { it.name == deckName }
            if (deck == null) {
                displayErrorAndCancel()
                return
            }
            selectDeckAndClose(deck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.deck_picker_dialog_list_item, parent, false)
            return ViewHolder(v.findViewById(R.id.deck_picker_dialog_list_item_value))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val deck = currentlyDisplayedDecks[position]
            holder.setDeck(deck)
        }

        override fun getItemCount(): Int {
            return currentlyDisplayedDecks.size
        }

        override fun getFilter(): Filter {
            return DecksFilter()
        }

        fun getCurrentlyDisplayedDecks(): List<SelectableDeck> {
            return currentlyDisplayedDecks
        }

        private inner class DecksFilter : TypedFilter<SelectableDeck>(allDecksList) {
            override fun filterResults(constraint: CharSequence, items: List<SelectableDeck>): List<SelectableDeck> {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                return items.filter {
                    it.name.lowercase(Locale.getDefault()).contains(filterPattern)
                }
            }

            override fun publishResults(constraint: CharSequence?, results: List<SelectableDeck>) {
                currentlyDisplayedDecks.apply {
                    clear()
                    addAll(results)
                    sort()
                }
                notifyDataSetChanged()
            }
        }

        init {
            allDecksList.addAll(deckNames)
            currentlyDisplayedDecks.addAll(deckNames)
            currentlyDisplayedDecks.sort()
        }
    }

    /**
     * @param deckId Either a deck id or ALL_DECKS_ID
     * @param name Name of the deck, or localization of "all decks"
     */
    @Parcelize
    class SelectableDeck(val deckId: DeckId, val name: String) : Comparable<SelectableDeck>, Parcelable {
        /**
         * The name to be displayed to the user. Contains
         * only the sub-deck name with proper indentation
         * rather than the entire deck name.
         * Eg: foo::bar -> \t\tbar
         */
        @IgnoredOnParcel
        val displayName: String by lazy {
            val nameArr = name.split("::")
            "\t\t".repeat(nameArr.size - 1) + nameArr[nameArr.size - 1]
        }

        constructor(d: DeckNameId) : this(d.id, d.name)

        /** "All decks" comes first. Then usual deck name order.  */
        override fun compareTo(other: SelectableDeck): Int {
            if (deckId == ALL_DECKS_ID) {
                return if (other.deckId == ALL_DECKS_ID) {
                    0
                } else {
                    -1
                }
            }
            return if (other.deckId == ALL_DECKS_ID) {
                1
            } else {
                DeckNameComparator.INSTANCE.compare(name, other.name)
            }
        }

        companion object {
            /**
             * @param includeFiltered Whether to include filtered decks in the output
             * @return all [SelectableDecks][SelectableDeck] in the collection satisfying the filter
             */
            suspend fun fromCollection(includeFiltered: Boolean): List<SelectableDeck> =
                withCol { decks.allNamesAndIds(includeFiltered = includeFiltered) }
                    .map { SelectableDeck(it) }
        }
    }

    fun interface DeckSelectionListener {
        fun onDeckSelected(deck: SelectableDeck?)
    }
    fun interface DeckCreationListener {
        fun onDeckCreated(deck: DeckNameId)
    }

    companion object {
        const val ALL_DECKS_ID = 0L
        private const val SUMMARY_MESSAGE = "summaryMessage"
        private const val TITLE = "title"
        private const val KEEP_RESTORE_DEFAULT_BUTTON = "keepRestoreDefaultButton"
        private const val DECK_NAMES = "deckNames"

        /**
         * A dialog which handles selecting a deck
         */
        fun newInstance(title: String, summaryMessage: String?, keepRestoreDefaultButton: Boolean, decks: List<SelectableDeck>): DeckSelectionDialog {
            val f = DeckSelectionDialog()
            val args = Bundle()
            args.putString(SUMMARY_MESSAGE, summaryMessage)
            args.putString(TITLE, title)
            args.putBoolean(KEEP_RESTORE_DEFAULT_BUTTON, keepRestoreDefaultButton)
            args.putParcelableArrayList(DECK_NAMES, ArrayList(decks))
            f.arguments = args
            return f
        }
    }
}
