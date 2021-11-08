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
import android.os.Parcel
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionGetter
import com.ichi2.libanki.Deck
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.stats.Stats
import com.ichi2.themes.Themes
import com.ichi2.utils.DeckNameComparator
import com.ichi2.utils.FilterResultsUtils
import com.ichi2.utils.FunctionalInterfaces
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.util.*
import java.util.Objects.requireNonNull

/**
 * The dialog which allow to select a deck. It is opened when the user click on a deck name in stats, browser or note editor.
 * It allows to filter decks by typing part of its name.
 */
open class DeckSelectionDialog : AnalyticsDialogFragment() {
    private var mDialog: MaterialDialog? = null
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
        var builder = MaterialDialog.Builder(requireActivity())
            .neutralText(R.string.dialog_cancel)
            .customView(dialogView, false)
        if (arguments.getBoolean(KEEP_RESTORE_DEFAULT_BUTTON)) {
            builder = builder.negativeText(R.string.restore_default).onNegative { _: MaterialDialog?, _: DialogAction? -> onDeckSelected(null) }
        }
        mDialog = builder.build()
        return mDialog!!
    }

    private fun getSummaryMessage(arguments: Bundle): String? {
        return arguments.getString(SUMMARY_MESSAGE)
    }

    private fun getDeckNames(arguments: Bundle): ArrayList<SelectableDeck> {
        return requireNonNull(arguments.getParcelableArrayList<SelectableDeck>(DECK_NAMES)) as ArrayList<SelectableDeck>
    }

    private val title: String?
        get() = requireNonNull(requireArguments().getString(TITLE))

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
        deckSelectionListener.onDeckSelected(deck)
    }

    private val deckSelectionListener: DeckSelectionListener
        get() {
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
            fun setDeck(deck: SelectableDeck) {
                deckTextView.text = deck.name
            }

            init {
                deckTextView.setOnClickListener {
                    val deckName = deckTextView.text.toString()
                    selectDeckByNameAndClose(deckName)
                }
                deckTextView.setOnLongClickListener { // creating sub deck with parent deck path
                    showSubDeckDialog(deckTextView.text.toString())
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
            val spinner = activity!!.findViewById<Spinner>(R.id.note_deck_spinner)
            val spinnerSelectedDeckName = spinner.selectedItem.toString()
            val unselectedTextColor = Themes.getColorFromAttr(context, android.R.attr.textColorPrimary)
            val selectedTextColor = ContextCompat.getColor(context!!, R.color.note_editor_selected_item_text)
            val unselectedBackgroundColor = Themes.getColorFromAttr(context, android.R.attr.colorBackground)
            val selectedBackgroundColor = ContextCompat.getColor(context!!, R.color.note_editor_selected_item_background)
            if (spinnerSelectedDeckName == deck.name) {
                holder.deckTextView.setBackgroundColor(selectedBackgroundColor)
                holder.deckTextView.setTextColor(selectedTextColor)
            } else {
                holder.deckTextView.setBackgroundColor(unselectedBackgroundColor)
                holder.deckTextView.setTextColor(unselectedTextColor)
            }
            holder.setDeck(deck)
        }

        override fun getItemCount(): Int {
            return mCurrentlyDisplayedDecks.size
        }

        override fun getFilter(): Filter {
            return DecksFilter()
        }

        /* Custom Filter class - as seen in http://stackoverflow.com/a/29792313/1332026 */
        private inner class DecksFilter : Filter() {
            private val mFilteredDecks: ArrayList<SelectableDeck> = ArrayList()
            override fun performFiltering(constraint: CharSequence): FilterResults {
                mFilteredDecks.clear()
                val allDecks = mAllDecksList
                if (constraint.isEmpty()) {
                    mFilteredDecks.addAll(allDecks)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim { it <= ' ' }
                    for (deck in allDecks) {
                        if (deck.name.lowercase(Locale.getDefault()).contains(filterPattern)) {
                            mFilteredDecks.add(deck)
                        }
                    }
                }
                return FilterResultsUtils.fromCollection(mFilteredDecks)
            }

            override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
                val currentlyDisplayedDecks = mCurrentlyDisplayedDecks
                currentlyDisplayedDecks.clear()
                currentlyDisplayedDecks.addAll(mFilteredDecks)
                currentlyDisplayedDecks.sort()
                notifyDataSetChanged()
            }
        }

        init {
            mAllDecksList.addAll(deckNames)
            mCurrentlyDisplayedDecks.addAll(deckNames)
            mCurrentlyDisplayedDecks.sort()
        }
    }
    @KotlinCleanup("auto parcel is needed")
    open class SelectableDeck : Comparable<SelectableDeck>, Parcelable {
        /**
         * Either a deck id or ALL_DECKS_ID
         */
        val deckId: Long

        /**
         * Name of the deck, or localization of "all decks"
         */
        val name: String

        constructor(deckId: Long, name: String) {
            this.deckId = deckId
            this.name = name
        }

        protected constructor(d: Deck) : this(d.getLong("id"), d.getString("name"))
        protected constructor(`in`: Parcel) {
            deckId = `in`.readLong()
            name = `in`.readString()!!
        }

        /** "All decks" comes first. Then usual deck name order.  */
        override fun compareTo(other: SelectableDeck): Int {
            if (deckId == Stats.ALL_DECKS_ID) {
                return if (other.deckId == Stats.ALL_DECKS_ID) {
                    0
                } else -1
            }
            return if (other.deckId == Stats.ALL_DECKS_ID) {
                1
            } else DeckNameComparator.INSTANCE.compare(name, other.name)
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(deckId)
            dest.writeString(name)
        }

        companion object {
            /**
             * @param filter A method deciding which deck to add
             * @return the list of all SelectableDecks from the collection satisfying filter
             */
            @JvmStatic
            @JvmOverloads
            fun fromCollection(c: Collection, filter: FunctionalInterfaces.Filter<Deck?> = FunctionalInterfaces.Filters.allowAll()): List<SelectableDeck> {
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

            val CREATOR: Parcelable.Creator<SelectableDeck?> = object : Parcelable.Creator<SelectableDeck?> {
                override fun createFromParcel(`in`: Parcel): SelectableDeck {
                    return SelectableDeck(`in`)
                }

                override fun newArray(size: Int): Array<SelectableDeck?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    interface DeckSelectionListener {
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
        @JvmStatic
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
