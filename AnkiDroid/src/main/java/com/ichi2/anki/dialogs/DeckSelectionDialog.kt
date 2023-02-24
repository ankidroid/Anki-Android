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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.DeckSelectionDialog.DecksArrayAdapter.DecksFilter
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getParcelableArrayListCompat
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.stats.Stats
import com.ichi2.utils.DeckNameComparator
import com.ichi2.utils.FunctionalInterfaces
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
    private var mDialog: MaterialDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    @Suppress("Deprecation") // Material dialog neutral button deprecation
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
        mDialog = MaterialDialog(requireActivity())
            .negativeButton(R.string.dialog_cancel)
            .customView(view = dialogView, noVerticalPadding = true)
        if (arguments.getBoolean(KEEP_RESTORE_DEFAULT_BUTTON)) {
            (mDialog as MaterialDialog).positiveButton(R.string.restore_default) {
                onDeckSelected(null)
            }
        }
        return mDialog!!
    }

    private fun getPositionOfDeck(did: DeckId, decks: List<SelectableDeck>) =
        decks.indexOfFirst { it.deckId == did }

    private fun getSummaryMessage(arguments: Bundle): String? {
        return arguments.getString(SUMMARY_MESSAGE)
    }

    private fun getDeckNames(arguments: Bundle): ArrayList<SelectableDeck> =
        arguments.getParcelableArrayListCompat(DECK_NAMES, SelectableDeck::class.java)!!

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
        try {
            // create subdeck
            val parentId = decks.id(parentDeckPath)
            val createDeckDialog = CreateDeckDialog(requireActivity(), R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, parentId)
            createDeckDialog.setOnNewDeckCreated { id: Long? ->
                // a sub deck was created
                selectDeckWithDeckName(decks.name(id!!))
            }
            createDeckDialog.showDialog()
        } catch (ex: DeckRenameException) {
            Timber.w(ex)
        }
    }

    private fun showDeckDialog() {
        val createDeckDialog = CreateDeckDialog(requireActivity(), R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
        // todo
        // setOnNewDeckCreated parameter to be made non null
        createDeckDialog.setOnNewDeckCreated { id: Long? ->
            // a deck was created
            selectDeckWithDeckName(decks.name(id!!))
        }
        createDeckDialog.showDialog()
    }

    protected fun requireCollectionGetter(): CollectionGetter {
        return requireContext() as CollectionGetter
    }

    protected val decks: DeckManager
        get() = requireCollectionGetter().col.decks

    /**
     * Create the deck if it does not exists.
     * If name is valid, send the deck with this name to listener and close the dialog.
     */
    private fun selectDeckWithDeckName(deckName: String) {
        try {
            val id = decks.id(deckName)
            val dec = SelectableDeck(id, deckName)
            selectDeckAndClose(dec)
        } catch (ex: DeckRenameException) {
            showThemedToast(requireActivity(), ex.getLocalizedMessage(resources), false)
        }
    }

    /**
     * @param deck deck sent to the listener.
     */
    protected fun onDeckSelected(deck: SelectableDeck?) {
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

    /**
     * Same action as pressing on the deck in the list. I.e. send the deck to listener and close the dialog.
     */
    protected fun selectDeckAndClose(deck: SelectableDeck) {
        onDeckSelected(deck)
        mDialog!!.dismiss()
    }

    protected fun displayErrorAndCancel() {
        mDialog!!.dismiss()
    }

    open inner class DecksArrayAdapter(deckNames: List<SelectableDeck>) : RecyclerView.Adapter<DecksArrayAdapter.ViewHolder>(), Filterable {
        inner class ViewHolder(val deckTextView: TextView) : RecyclerView.ViewHolder(deckTextView) {
            var deckName: String = ""
            var deckID: Long = -1L

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

        private val mAllDecksList = ArrayList<SelectableDeck>()
        private val mCurrentlyDisplayedDecks = ArrayList<SelectableDeck>()
        protected fun selectDeckByNameAndClose(deckName: String) {
            for (d in mAllDecksList) {
                if (d.name == deckName) {
                    selectDeckAndClose(d)
                    return
                }
            }
            displayErrorAndCancel()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.deck_picker_dialog_list_item, parent, false)
            return ViewHolder(v.findViewById(R.id.deck_picker_dialog_list_item_value))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val deck = mCurrentlyDisplayedDecks[position]
            holder.setDeck(deck)
        }

        override fun getItemCount(): Int {
            return mCurrentlyDisplayedDecks.size
        }

        override fun getFilter(): Filter {
            return DecksFilter()
        }

        fun getCurrentlyDisplayedDecks(): List<SelectableDeck> {
            return mCurrentlyDisplayedDecks
        }

        private inner class DecksFilter : TypedFilter<SelectableDeck>(mAllDecksList) {
            override fun filterResults(constraint: CharSequence, items: List<SelectableDeck>): List<SelectableDeck> {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                return items.filter {
                    it.name.lowercase(Locale.getDefault()).contains(filterPattern)
                }
            }

            override fun publishResults(constraint: CharSequence?, results: List<SelectableDeck>) {
                mCurrentlyDisplayedDecks.apply {
                    clear()
                    addAll(results)
                    sort()
                }
                notifyDataSetChanged()
            }
        }

        init {
            mAllDecksList.addAll(deckNames)
            mCurrentlyDisplayedDecks.addAll(deckNames)
            mCurrentlyDisplayedDecks.sort()
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

        constructor(d: Deck) : this(d.getLong("id"), d.getString("name"))

        /** "All decks" comes first. Then usual deck name order.  */
        override fun compareTo(other: SelectableDeck): Int {
            if (deckId == Stats.ALL_DECKS_ID) {
                return if (other.deckId == Stats.ALL_DECKS_ID) {
                    0
                } else {
                    -1
                }
            }
            return if (other.deckId == Stats.ALL_DECKS_ID) {
                1
            } else {
                DeckNameComparator.INSTANCE.compare(name, other.name)
            }
        }

        companion object {
            /**
             * @param filter A method deciding which deck to add
             * @return the list of all SelectableDecks from the collection satisfying filter
             */
            fun fromCollection(c: Collection, filter: FunctionalInterfaces.Filter<Deck> = FunctionalInterfaces.Filters.allowAll()): List<SelectableDeck> {
                val all = c.decks.all()
                val ret: MutableList<SelectableDeck> = ArrayList(all.size)
                for (d in all) {
                    if (!filter.shouldInclude(d)) {
                        continue
                    }
                    ret.add(SelectableDeck(d))
                }
                return ret
            }
        }
    }

    fun interface DeckSelectionListener {
        fun onDeckSelected(deck: SelectableDeck?)
    }

    companion object {
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
