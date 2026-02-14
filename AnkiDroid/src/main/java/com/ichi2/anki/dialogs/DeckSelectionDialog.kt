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
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.decks.deckTreeNode
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.databinding.DeckPickerDialogListItemBinding
import com.ichi2.anki.databinding.DialogDeckPickerBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog.DecksArrayAdapter.DecksFilter
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.TypedFilter
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber
import java.util.Locale

/**
 * "Deck Search": A dialog allowing the user to select a deck from a list of decks.
 *
 * * Allows filtering of visible decks based on name (searching): [DecksFilter]
 * * Allows adding a new deck: [showDeckDialog]
 * * Allows adding a subdeck via long-pressing a deck: [showSubDeckDialog]
 *
 * It is opened when the user wants a deck in stats, browser or note editor.
 *
 * @see com.ichi2.anki.model.SelectableDeck The data that is displayed
 */
@NeedsTest("simulate 'don't keep activities'")
@NeedsTest("Test the ordering of the dialog")
@NeedsTest("test the ordering of decks in search page in the dialog")
@NeedsTest("test syncing the status of collapsing deck with teh deckPicker")
open class DeckSelectionDialog : AnalyticsDialogFragment() {
    private lateinit var binding: DialogDeckPickerBinding
    private var dialog: AlertDialog? = null
    private lateinit var expandImage: Drawable
    private lateinit var collapseImage: Drawable
    private lateinit var decksRoot: DeckNode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true

        val attrs =
            intArrayOf(
                R.attr.expandRef,
                R.attr.collapseRef,
            )
        val typedArray = requireContext().obtainStyledAttributes(attrs)
        expandImage = typedArray.getDrawable(0)!!
        expandImage.isAutoMirrored = true
        collapseImage = typedArray.getDrawable(1)!!
        collapseImage.isAutoMirrored = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogDeckPickerBinding.inflate(LayoutInflater.from(context))
        val summary = binding.summary
        val arguments = requireArguments()
        if (getSummaryMessage(arguments) == null) {
            summary.visibility = View.GONE
        } else {
            summary.visibility = View.VISIBLE
            summary.text = getSummaryMessage(arguments)
        }
        binding.list.requestFocus()
        val deckLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(requireActivity())
        binding.list.layoutManager = deckLayoutManager
        val dividerItemDecoration = DividerItemDecoration(binding.list.context, DividerItemDecoration.VERTICAL)
        binding.list.addItemDecoration(dividerItemDecoration)
        val decks: List<SelectableDeck> = getDeckNames(arguments)
        val adapter = DecksArrayAdapter(decks)
        binding.list.adapter = adapter
        adjustToolbar(binding.root, adapter)
        dialog =
            AlertDialog.Builder(requireActivity()).create {
                negativeButton(R.string.dialog_cancel)
                customView(view = binding.root)
                if (arguments.getBoolean(KEEP_RESTORE_DEFAULT_BUTTON)) {
                    positiveButton(R.string.restore_default) {
                        onDeckSelected(null)
                    }
                }
            }
        return dialog!!
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        )
    }

    private fun getSummaryMessage(arguments: Bundle): String? = arguments.getString(SUMMARY_MESSAGE)

    private fun getDeckNames(arguments: Bundle): ArrayList<SelectableDeck> =
        BundleCompat.getParcelableArrayList(arguments, DECK_NAMES, SelectableDeck::class.java)!!

    private val title: String
        get() = requireArguments().getString(TITLE)!!

    private fun adjustToolbar(
        dialogView: View,
        adapter: DecksArrayAdapter,
    ) {
        val toolbar: Toolbar = binding.toolbar
        toolbar.title = title
        toolbar.inflateMenu(R.menu.deck_picker_dialog_menu)
        val searchItem = toolbar.menu.findItem(R.id.deck_picker_dialog_action_filter)
        val searchView = searchItem.actionView as AccessibleSearchView
        searchView.queryHint = getString(R.string.deck_picker_dialog_filter_decks)
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    searchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    adapter.filter.filter(newText)
                    return true
                }
            },
        )
        val addDecks = toolbar.menu.findItem(R.id.deck_picker_dialog_action_add_deck)
        addDecks.setOnMenuItemClickListener {
            // creating new deck without any parent deck
            showDeckDialog()
            true
        }
    }

    /**
     * Displays a dialog to create a subdeck under the specified parent deck.
     *
     * @param parentDeck The parent deck under which the subdeck will be created
     */
    private fun showSubDeckDialog(parentDeck: SelectableDeck.Deck) {
        val createDeckDialog =
            CreateDeckDialog(requireActivity(), R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, parentDeck.deckId)
        createDeckDialog.onNewDeckCreated = { did: DeckId -> onNewDeckCreated(did) }
        createDeckDialog.showDialog()
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
            val deck = SelectableDeck.Deck(id, name)
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
            val parentFragment = parentFragment
            if (parentFragment is DeckSelectionListener) {
                return parentFragment
            }
            val activity: Activity = requireActivity()
            if (activity is DeckSelectionListener) {
                return activity
            }
            // try to find inside the activity an active fragment that is a DeckSelectionListener
            val foundAvailableFragments =
                parentFragmentManager.fragments.filter {
                    it.isResumed && it is DeckSelectionListener
                }
            if (foundAvailableFragments.isNotEmpty()) {
                // if we found at least one resumed candidate fragment use it
                return foundAvailableFragments[0] as DeckSelectionListener
            }
            throw IllegalStateException("Neither activity or any fragment in the activity were a selection listener")
        }

    var deckCreationListener: DeckCreationListener? = null

    /**
     * Same action as pressing on the deck in the list. I.e. send the deck to listener and close the dialog.
     */
    protected fun selectDeckAndClose(deck: SelectableDeck) {
        Timber.d("selected deck '%s'", deck)
        onDeckSelected(deck)
        dialog!!.dismiss()
    }

    protected fun displayErrorAndCancel() {
        dialog!!.dismiss()
    }

    open inner class DecksArrayAdapter(
        decks: List<SelectableDeck>,
    ) : RecyclerView.Adapter<DecksArrayAdapter.ViewHolder>(),
        Filterable {
        inner class ViewHolder(
            private val binding: DeckPickerDialogListItemBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            private var currentDeck: SelectableDeck? = null

            val expander: ImageButton = binding.expander
            val indentView: ImageButton = binding.indent

            fun setDeck(deck: SelectableDeck) {
                binding.deckTextView.text = deck.getDisplayName(requireContext())
                currentDeck = deck
            }

            init {
                binding.root.setOnClickListener {
                    currentDeck?.let { selectDeckAndClose(it) }
                }
                expander.setOnClickListener {
                    currentDeck?.let { toggleExpansion(it) }
                }
                binding.root.setOnContextAndLongClickListener {
                    // creating sub deck with parent deck path
                    currentDeck?.let { deck ->
                        if (deck is SelectableDeck.Deck) {
                            showSubDeckDialog(deck)
                        }
                    }

                    true
                }
            }

            private fun toggleExpansion(deck: SelectableDeck) {
                val deckId =
                    when (deck) {
                        is SelectableDeck.AllDecks -> return
                        is SelectableDeck.Deck -> deck.deckId
                    }
                decksRoot.find(deckId)?.apply {
                    collapsed = !collapsed
                    Timber.d("The deck with ID $id is currently expanded: ${!collapsed}.")
                    updateCurrentlyDisplayedDecks()
                }
            }
        }

        private fun updateCurrentlyDisplayedDecks() {
            currentlyDisplayedDecks.clear()
            currentlyDisplayedDecks.addAll(allDecksList.filter(::isViewable))
            notifyDataSetChanged()
        }

        private val allDecksList = ArrayList<DeckNode>()
        private val currentlyDisplayedDecks = ArrayList<DeckNode>()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val layoutInflater = LayoutInflater.from(context)
            val binding = DeckPickerDialogListItemBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val deck = currentlyDisplayedDecks[position]
            val isDeckViewable = isViewable(deck)
            holder.itemView.isVisible = isDeckViewable
            if (isDeckViewable) {
                val model = if (deck.did == ALL_DECKS_ID) SelectableDeck.AllDecks else SelectableDeck.Deck(deck.did, deck.fullDeckName)
                holder.setDeck(model)
            }
            setDeckExpander(holder.expander, holder.indentView, deck)
        }

        /**
         * Sets the expander and indent views based on the properties of the provided DeckNode.
         *
         * @param expander The ImageButton used for expanding/collapsing the deck node.
         * @param indent The ImageButton used for indenting the deck node.
         * @param node The DeckNode representing the deck.
         */
        private fun setDeckExpander(
            expander: ImageButton,
            indent: ImageButton,
            node: DeckNode,
        ) {
            if (hasSubDecks(node)) {
                expander.apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    setImageDrawable(if (node.collapsed) expandImage else collapseImage)
                    contentDescription = context.getString(if (node.collapsed) R.string.expand else R.string.collapse)
                    visibility = View.VISIBLE
                }
            } else {
                expander.apply {
                    visibility = View.INVISIBLE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }
            indent.minimumWidth = node.depth * expander.resources.getDimensionPixelSize(R.dimen.keyline_1)
        }

        private fun hasSubDecks(node: DeckNode): Boolean = node.children.isNotEmpty()

        private fun isViewable(deck: DeckNode): Boolean {
            val parentNodeRef = deck.parent ?: return true
            // The parent belongs to the tree retained by [allDecksList], so should still exist.
            val parentNode = parentNodeRef.get()!!
            return !parentNode.collapsed && isViewable(parentNode)
        }

        override fun getItemCount(): Int = currentlyDisplayedDecks.size

        override fun getFilter(): Filter = DecksFilter()

        private inner class DecksFilter : TypedFilter<DeckNode>(allDecksList) {
            override fun filterResults(
                constraint: CharSequence,
                items: List<DeckNode>,
            ): List<DeckNode> {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                return items.filter {
                    it.fullDeckName.lowercase(Locale.getDefault()).contains(filterPattern)
                }
            }

            override fun publishResults(
                constraint: CharSequence?,
                results: List<DeckNode>,
            ) {
                results.forEach { it.collapsed = false }
                currentlyDisplayedDecks.apply {
                    clear()
                    addAll(results)
                }
                notifyDataSetChanged()
            }
        }

        init {
            launchCatchingTask {
                decksRoot = withCol { Pair(sched.deckDueTree(), isEmpty) }.first
                val allDecksSet =
                    decks
                        .mapNotNull { it as? SelectableDeck.Deck }
                        .mapNotNull { decksRoot.find(it.deckId) }
                        .toSet()
                if (decks.any { it is SelectableDeck.AllDecks }) {
                    val newDeckNode =
                        deckTreeNode {
                            deckId = ALL_DECKS_ID
                            name = "all"
                        }
                    allDecksList.add(DeckNode(newDeckNode, getString(R.string.card_browser_all_decks), null))
                }

                allDecksList.addAll(allDecksSet)
                updateCurrentlyDisplayedDecks()
            }
        }
    }

    // TODO: allow filtering to SelectableDeck.Deck, excluding 'AllDecks'

    fun interface DeckSelectionListener {
        fun onDeckSelected(deck: SelectableDeck?)
    }

    fun interface DeckCreationListener {
        fun onDeckCreated(deck: DeckNameId)
    }

    companion object {
        private const val SUMMARY_MESSAGE = "summaryMessage"
        private const val TITLE = "title"
        private const val KEEP_RESTORE_DEFAULT_BUTTON = "keepRestoreDefaultButton"
        private const val DECK_NAMES = "deckNames"

        /**
         * A dialog which handles selecting a deck
         */
        fun newInstance(
            title: String,
            summaryMessage: String?,
            keepRestoreDefaultButton: Boolean,
            decks: List<SelectableDeck>,
        ): DeckSelectionDialog {
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
