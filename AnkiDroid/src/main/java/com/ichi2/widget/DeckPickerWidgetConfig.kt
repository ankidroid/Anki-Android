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

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Activity for configuring the Deck Picker Widget.
 * This activity allows the user to select decks from deck selection dialog to be displayed in the widget.
 * User can Select up to 5 decks.
 * User Can remove, reorder decks and reconfigure by holding the widget .
 */
class DeckPickerWidgetConfig : AnkiActivity(), DeckSelectionListener {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    lateinit var deckAdapter: WidgetConfigScreenAdapter
    private lateinit var deckPickerWidgetPreferences: WidgetPreferences
    private val MAX_DECKS_ALLOWED = 5 // Maximum number of decks allowed in the widget

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_deck_picker_config)

        deckPickerWidgetPreferences = WidgetPreferences(this)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }

        deckAdapter = WidgetConfigScreenAdapter()
        findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DeckPickerWidgetConfig.deckAdapter
            val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }

        findViewById<Button>(R.id.done_button).setOnClickListener {
            saveSelectedDecksToPreferencesDeckPickerWidget()

            val selectedDeckIds = deckPickerWidgetPreferences.getSelectedDeckIdsFromPreferencesDeckPickerWidget(appWidgetId)

            val appWidgetManager = AppWidgetManager.getInstance(this)
            DeckPickerWidget.updateWidget(this, appWidgetManager, appWidgetId, selectedDeckIds)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)

            // Send broadcast to update widget
            sendBroadcast(Intent(this, DeckPickerWidget::class.java))

            finish()
        }

        findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker).setOnClickListener {
            showDeckSelectionDialog()
        }

        // Load and display saved preferences
        loadSavedPreferences()

        updateViewVisibility()

        // Register broadcast receiver to handle widget deletion
        registerReceiver(widgetRemovedReceiver, IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    fun loadSavedPreferences() {
        val selectedDeckIds = deckPickerWidgetPreferences.getSelectedDeckIdsFromPreferencesDeckPickerWidget(appWidgetId)
        if (selectedDeckIds.isNotEmpty()) {
            lifecycleScope.launch {
                val decks = fetchDecks()
                val selectedDecks = decks.filter { it.deckId in selectedDeckIds }
                selectedDecks.forEach { deckAdapter.addDeck(it) }
                updateViewVisibility()
            }
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
            SelectableDeck.fromCollection(includeFiltered = false)
        }
    }

    /** Displays the deck selection dialog with the provided list of decks. */
    private fun displayDeckSelectionDialog(decks: List<SelectableDeck>) {
        val dialog = DeckSelectionDialog.newInstance(
            title = getString(R.string.select_deck_title),
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

        if (deckAdapter.itemCount >= MAX_DECKS_ALLOWED) {
            val message = resources.getQuantityString(R.plurals.deck_limit_reached, MAX_DECKS_ALLOWED, MAX_DECKS_ALLOWED)
            showSnackbar(message)
        } else {
            deckAdapter.addDeck(deck)
            updateViewVisibility()
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
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.bindingAdapterPosition
            val toPosition = target.bindingAdapterPosition
            deckAdapter.moveDeck(fromPosition, toPosition)
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
        // Get the list of selected deck IDs as strings
        val selectedDecks = deckAdapter.deckIds.map { it.toString() }
        deckPickerWidgetPreferences.saveSelectedDecks(appWidgetId, selectedDecks)
        val updateIntent = Intent(this, DeckPickerWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))

            // Pass the selected deck IDs as a long array in the intent
            putExtra("deck_picker_widget_selected_deck_ids", selectedDecks.map { it.toLong() }.toLongArray())
        }

        // Send the broadcast to update the widget
        sendBroadcast(updateIntent)
    }

    /** Deletes the widget data associated with the given app widget ID. */
    private fun deleteWidgetDataDeckPickerWidget(appWidgetId: Int) {
        deckPickerWidgetPreferences.deleteDeckPickerWidgetData(appWidgetId)
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

            context?.let { deleteWidgetDataDeckPickerWidget(appWidgetId) }
        }
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
