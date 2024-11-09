/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget.deckpicker

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.isDefaultDeckEmpty
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.widget.WidgetConfigScreenAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Activity for configuring the Deck Picker Widget.
 * This activity allows the user to select decks from deck selection dialog to be displayed in the widget.
 * User can Select up to 5 decks.
 * User Can remove, reorder decks and reconfigure by holding the widget.
 */
class DeckPickerWidgetConfig : AnkiActivity(), DeckSelectionListener, BaseSnackbarBuilderProvider {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    lateinit var deckAdapter: WidgetConfigScreenAdapter
    private lateinit var deckPickerWidgetPreferences: DeckPickerWidgetPreferences

    private var hasUnsavedChanges = false
    private var isAdapterObserverRegistered = false
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    /** Tracks coroutine running [initializeUIComponents]: must be run on a non-empty collection */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal lateinit var initTask: Job

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)

        if (!ensureStoragePermissions()) {
            return
        }

        setContentView(R.layout.widget_deck_picker_config)

        deckPickerWidgetPreferences = DeckPickerWidgetPreferences(this)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }

        // Check if the collection is empty before proceeding and if the collection is empty, show a toast instead of the configuration view.
        this.initTask = lifecycleScope.launch {
            if (isCollectionEmpty()) {
                Timber.w("Closing: Collection is empty")
                showThemedToast(
                    this@DeckPickerWidgetConfig,
                    R.string.app_not_initialized_new,
                    false
                )
                finish()
                return@launch
            }

            initializeUIComponents()
        }
    }

    fun showSnackbar(message: CharSequence) {
        showSnackbar(
            message,
            Snackbar.LENGTH_LONG
        )
    }

    fun showSnackbar(messageResId: Int) {
        showSnackbar(getString(messageResId))
    }

    private fun initializeUIComponents() {
        deckAdapter = WidgetConfigScreenAdapter { deck, position ->
            deckAdapter.removeDeck(deck.deckId)
            showSnackbar(R.string.deck_removed_from_widget)
            updateViewVisibility()
            updateFabVisibility()
            updateDoneButtonVisibility()
            hasUnsavedChanges = true
            setUnsavedChanges(true)
        }

        findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DeckPickerWidgetConfig.deckAdapter
            val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }

        setupDoneButton()

        // TODO: Implement multi-select functionality so that user can select desired decks in once.
        // TODO: Implement a functionality to hide already selected deck.
        findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker).setOnClickListener {
            showDeckSelectionDialog()
        }

        lifecycleScope.launch { updateViewWithSavedPreferences() }

        // Update the visibility of the "no decks" placeholder and the widget configuration container
        updateViewVisibility()

        registerReceiver(widgetRemovedReceiver, IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED))

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges) {
                    DiscardChangesDialog.showDialog(
                        context = this@DeckPickerWidgetConfig,
                        positiveMethod = {
                            // Set flag to indicate that changes are discarded
                            hasUnsavedChanges = false
                            finish()
                        }
                    )
                } else {
                    finish()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Register the AdapterDataObserver if not already registered
        if (!isAdapterObserverRegistered) {
            deckAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    updateDoneButtonVisibility() // Update visibility when data changes
                }
            })
            isAdapterObserverRegistered = true
        }
    }

    private fun updateCallbackState() {
        onBackPressedCallback.isEnabled = hasUnsavedChanges
    }

    // Call this method when there are unsaved changes
    private fun setUnsavedChanges(unsaved: Boolean) {
        hasUnsavedChanges = unsaved
        updateCallbackState()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker)
    }

    /**
     * Configures the "Done" button based on the number of selected decks.
     *
     *   If no decks are selected: The button is hidden.
     *   If decks are selected: The button is visible with the text "Save".
     *   When clicked, the selected decks are saved, the widget is updated,
     *   and the activity is finished.
     */
    private fun setupDoneButton() {
        val doneButton = findViewById<Button>(R.id.submit_button)
        val saveText = getString(R.string.save).uppercase()

        // Set the button text and click listener only once during initialization
        doneButton.text = saveText
        doneButton.setOnClickListener {
            saveSelectedDecksToPreferencesDeckPickerWidget()
            hasUnsavedChanges = false
            setUnsavedChanges(false)

            val selectedDeckIds = deckPickerWidgetPreferences.getSelectedDeckIdsFromPreferences(appWidgetId)

            val appWidgetManager = AppWidgetManager.getInstance(this)
            DeckPickerWidget.updateWidget(this, appWidgetManager, appWidgetId, selectedDeckIds)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        // Initially set the visibility based on the number of selected decks
        updateDoneButtonVisibility()
    }

    private fun updateDoneButtonVisibility() {
        val doneButton = findViewById<Button>(R.id.submit_button)
        doneButton.isVisible = deckAdapter.itemCount != 0
    }

    /** Updates the visibility of the FloatingActionButton based on the number of selected decks */
    private fun updateFabVisibility() {
        lifecycleScope.launch {
            val defaultDeckEmpty = isDefaultDeckEmpty()

            val totalSelectableDecks = getTotalSelectableDecks()

            // Adjust totalSelectableDecks if the default deck is empty
            var adjustedTotalSelectableDecks = totalSelectableDecks
            if (defaultDeckEmpty) {
                adjustedTotalSelectableDecks -= 1
            }

            val selectedDeckCount = deckAdapter.itemCount

            val fab = findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker)
            fab.isVisible = !(selectedDeckCount >= MAX_DECKS_ALLOWED || selectedDeckCount >= adjustedTotalSelectableDecks)
        }
    }

    /**
     * Returns the total number of selectable decks.
     *
     * The operation involves accessing the collection, which might be a time-consuming
     * I/O-bound task. Hence, we switch to the IO dispatcher
     * to avoid blocking the main thread and ensure a smooth user experience.
     */
    private suspend fun getTotalSelectableDecks(): Int {
        return withContext(Dispatchers.IO) {
            SelectableDeck.fromCollection(includeFiltered = false).size
        }
    }

    /** Updates the view according to the saved preference for appWidgetId.*/
    suspend fun updateViewWithSavedPreferences() {
        val selectedDeckIds = deckPickerWidgetPreferences.getSelectedDeckIdsFromPreferences(appWidgetId)
        if (selectedDeckIds.isNotEmpty()) {
            val decks = fetchDecks()
            val selectedDecks = decks.filter { it.deckId in selectedDeckIds }
            selectedDecks.forEach { deckAdapter.addDeck(it) }
            updateViewVisibility()
            updateFabVisibility()
            setupDoneButton()
        }
    }

    /** Asynchronously displays the list of deck in the selection dialog. */
    private fun showDeckSelectionDialog() {
        lifecycleScope.launch {
            val decks = fetchDecks()
            displayDeckSelectionDialog(decks)
        }
    }

    /** Returns the list of standard deck. */
    private suspend fun fetchDecks(): List<SelectableDeck> {
        return withContext(Dispatchers.IO) {
            SelectableDeck.fromCollection(includeFiltered = true)
        }
    }

    /** Displays the deck selection dialog with the provided list of decks. */
    private fun displayDeckSelectionDialog(decks: List<SelectableDeck>) {
        val dialog = DeckSelectionDialog.newInstance(
            title = getString(R.string.select_decks_title),
            summaryMessage = null,
            keepRestoreDefaultButton = false,
            decks = decks
        )
        dialog.show(supportFragmentManager, "DeckSelectionDialog")
    }

    /** Called when a deck is selected from the deck selection dialog. */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }

        val isDeckAlreadySelected = deckAdapter.deckIds.contains(deck.deckId)

        if (isDeckAlreadySelected) {
            // TODO: Eventually, ensure that the user can't select a deck that is already selected.
            showSnackbar(getString(R.string.deck_already_selected_message))
            return
        }

        // Check if the deck is being added to a fully occupied selection
        if (deckAdapter.itemCount >= MAX_DECKS_ALLOWED) {
            // Snackbar will only be shown when adding the 5th deck
            if (deckAdapter.itemCount == MAX_DECKS_ALLOWED) {
                showSnackbar(resources.getQuantityString(R.plurals.deck_limit_reached, MAX_DECKS_ALLOWED, MAX_DECKS_ALLOWED))
            }
            // The FAB visibility should be handled in updateFabVisibility()
        } else {
            // Add the deck and update views
            deckAdapter.addDeck(deck)
            updateViewVisibility()
            updateFabVisibility()
            setupDoneButton()
            hasUnsavedChanges = true
            setUnsavedChanges(true)

            // Show snackbar if the deck is the 5th deck
            if (deckAdapter.itemCount == MAX_DECKS_ALLOWED) {
                showSnackbar(resources.getQuantityString(R.plurals.deck_limit_reached, MAX_DECKS_ALLOWED, MAX_DECKS_ALLOWED))
            }
        }
    }

    /** Updates the visibility of the "no decks" placeholder and the widget configuration container */
    fun updateViewVisibility() {
        val noDecksPlaceholder = findViewById<View>(R.id.no_decks_placeholder)
        val widgetConfigContainer = findViewById<View>(R.id.widgetConfigContainer)

        if (deckAdapter.itemCount > 0) {
            noDecksPlaceholder.visibility = View.GONE
            widgetConfigContainer.visibility = View.VISIBLE
        } else {
            noDecksPlaceholder.visibility = View.VISIBLE
            widgetConfigContainer.visibility = View.GONE
        }
    }

    /** ItemTouchHelper callback for handling drag and drop of decks. */
    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val selectedDeckCount = deckAdapter.itemCount
            return if (selectedDeckCount > 1) {
                super.getDragDirs(recyclerView, viewHolder)
            } else {
                0 // Disable drag if there's only one item
            }
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            deckAdapter.moveDeck(fromPosition, toPosition)
            hasUnsavedChanges = true
            setUnsavedChanges(true)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // No swipe action
        }
    }

    /**
     * Saves the selected deck IDs to SharedPreferences and triggers a widget update.
     *
     * This function retrieves the selected decks from the `deckAdapter`, converts their IDs
     * to a comma-separated string, and stores it in SharedPreferences.
     * It then sends a broadcast to update the widget with the new deck selection.
     */
    fun saveSelectedDecksToPreferencesDeckPickerWidget() {
        val selectedDecks = deckAdapter.deckIds.map { it }
        deckPickerWidgetPreferences.saveSelectedDecks(appWidgetId, selectedDecks.map { it.toString() })

        val updateIntent = Intent(this, DeckPickerWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))

            putExtra("deck_picker_widget_selected_deck_ids", selectedDecks.toList().toLongArray())
        }

        sendBroadcast(updateIntent)
    }

    /** BroadcastReceiver to handle widget removal. */
    private val widgetRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                return
            }

            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                return
            }

            context?.let { deckPickerWidgetPreferences.deleteDeckData(appWidgetId) }
        }
    }
    companion object {
        /**
         * Maximum number of decks allowed in the widget.
         */
        private const val MAX_DECKS_ALLOWED = 5
    }
}

/**
 * Unregisters a broadcast receiver from the context silently.
 *
 * This extension function attempts to unregister a broadcast receiver from the context
 * without throwing an exception if the receiver is not registered.
 * It catches the `IllegalArgumentException` that is thrown when attempting to unregister
 * a receiver that is not registered, allowing the operation to fail gracefully without crashing.
 *
 * @param receiver The broadcast receiver to be unregistered.
 *
 * @see ContextWrapper.unregisterReceiver
 * @see IllegalArgumentException
 */
fun ContextWrapper.unregisterReceiverSilently(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        Timber.d(e, "unregisterReceiverSilently")
    }
}
