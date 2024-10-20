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

package com.ichi2.widget.cardanalysis

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
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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

// TODO: Ensure that the Deck Selection Dialog does not close automatically while the user is interacting with it.

class CardAnalysisWidgetConfig : AnkiActivity(), DeckSelectionListener, BaseSnackbarBuilderProvider {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    lateinit var deckAdapter: WidgetConfigScreenAdapter
    private lateinit var cardAnalysisWidgetPreferences: CardAnalysisWidgetPreferences

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

        cardAnalysisWidgetPreferences = CardAnalysisWidgetPreferences(this)

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
                    this@CardAnalysisWidgetConfig,
                    R.string.app_not_initialized_new,
                    false
                )
                finish()
                return@launch
            }

            initializeUIComponents()
            // Show the Deck selection dialog only when there are no decks selected while opening the configuration screen.
            val selectedDeckId = cardAnalysisWidgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId)
            if (selectedDeckId == null) {
                showDeckSelectionDialog()
            }
        }
    }

    fun showSnackbar(message: CharSequence) {
        showSnackbar(
            message,
            Snackbar.LENGTH_LONG
        )
    }

    fun showSnackbar(@StringRes messageResId: Int) {
        showSnackbar(getString(messageResId))
    }

    private fun initializeUIComponents() {
        deckAdapter = WidgetConfigScreenAdapter { deck, _ ->
            deckAdapter.removeDeck(deck.deckId)
            showSnackbar(R.string.deck_removed_from_widget)
            updateViewVisibility()
            updateFabVisibility()
            updateSubmitButtonText()
            setUnsavedChanges(true)
        }

        findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CardAnalysisWidgetConfig.deckAdapter
        }

        // Find and update the submit button text based on the initial deck selection state
        val submitButton = findViewById<Button>(R.id.submit_button)
        updateSubmitButtonText()

        submitButton.setOnClickListener {
            finish() // Close the configuration screen when the button is clicked
        }

        findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker).setOnClickListener {
            showDeckSelectionDialog()
        }

        lifecycleScope.launch { updateViewWithSavedPreferences() }

        // Update the visibility of the "no decks" placeholder and the widget configuration container
        updateViewVisibility()

        registerReceiver(widgetRemovedReceiver, IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED))

        onBackPressedCallback = object : OnBackPressedCallback(hasUnsavedChanges) {
            override fun handleOnBackPressed() {
                if (isEnabled) {
                    showDiscardChangesDialog()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Register the AdapterDataObserver if not already registered
        if (!isAdapterObserverRegistered) {
            deckAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                }
            })
            isAdapterObserverRegistered = true
        }
    }

    /** Updates the text of the submit button based on the selected deck count. */
    private fun updateSubmitButtonText() {
        val submitButton = findViewById<Button>(R.id.submit_button)
        if (deckAdapter.itemCount > 0) {
            submitButton.text = getString(R.string.dialog_cancel)
            submitButton.visibility = View.VISIBLE
        } else {
            submitButton.visibility = View.GONE
        }
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(
            context = this@CardAnalysisWidgetConfig,
            positiveMethod = {
                // Discard changes and finish the activity
                hasUnsavedChanges = false
                finish()
            }
        )
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

    /** Updates the visibility of the FloatingActionButton based on the number of selected decks */
    private fun updateFabVisibility() {
        // Directly check if there's exactly one deck selected
        val selectedDeckCount = deckAdapter.itemCount

        // Find the FloatingActionButton by its ID
        val fab = findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker)

        // Make the FAB visible only if no deck is selected (allow adding one deck)
        fab.isVisible = selectedDeckCount == 0
    }

    /** Updates the view according to the saved preference for appWidgetId.*/
    suspend fun updateViewWithSavedPreferences() {
        val selectedDeckId = cardAnalysisWidgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId) ?: return

        val decks = fetchDecks()
        val selectedDecks = decks.filter { it.deckId == selectedDeckId }
        selectedDecks.forEach { deckAdapter.addDeck(it) }
        updateViewVisibility()
        updateFabVisibility()
        updateSubmitButtonText()
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

    /**
     * Called when a deck is selected from the deck selection dialog.
     *
     * This method adds the selected deck to the `deckAdapter`, updates the visibility of views,
     * and immediately saves the selected deck to preferences.
     *
     * @param deck The selected deck, or `null` if no deck was selected.
     */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }

        // Check if the deck is being added to a fully occupied selection
        if (deckAdapter.itemCount >= MAX_DECKS_ALLOWED) {
            return
        } else {
            // Add the deck and update views
            deckAdapter.addDeck(deck)
            updateViewVisibility()
            updateFabVisibility()
            setUnsavedChanges(true)

            // Save the selected deck immediately
            saveSelectedDecksToPreferencesCardAnalysisWidget()
            setUnsavedChanges(false)

            // Update the widget with the new selected deck ID
            cardAnalysisWidgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId)
            val appWidgetManager = AppWidgetManager.getInstance(this)
            CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    /** Updates the visibility of the "no decks" placeholder and the widget configuration container */
    fun updateViewVisibility() {
        val noDecksPlaceholder = findViewById<View>(R.id.no_decks_placeholder)
        val widgetConfigContainer = findViewById<View>(R.id.widgetConfigContainer)

        noDecksPlaceholder.isVisible = deckAdapter.itemCount == 0
        widgetConfigContainer.isVisible = deckAdapter.itemCount > 0
    }

    fun saveSelectedDecksToPreferencesCardAnalysisWidget() {
        val selectedDeck = deckAdapter.deckIds.getOrNull(0)
        cardAnalysisWidgetPreferences.saveSelectedDeck(appWidgetId, selectedDeck)

        val updateIntent = Intent(this, CardAnalysisWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            putExtra(EXTRA_SELECTED_DECK_IDS, selectedDeck)
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

            cardAnalysisWidgetPreferences.deleteDeckData(appWidgetId)
        }
    }

    companion object {
        /**
         * Maximum number of decks allowed in the widget.
         */
        private const val MAX_DECKS_ALLOWED = 1
        private const val EXTRA_SELECTED_DECK_IDS = "card_analysis_widget_selected_deck_ids"
    }
}

fun ContextWrapper.unregisterReceiverSilently(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        Timber.d(e, "unregisterReceiverSilently")
    }
}
