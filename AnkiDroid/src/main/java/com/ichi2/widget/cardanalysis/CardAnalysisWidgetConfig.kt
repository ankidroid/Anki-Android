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

class CardAnalysisWidgetConfig :
    AnkiActivity(),
    DeckSelectionListener,
    BaseSnackbarBuilderProvider,
    CardAnalysisWidgetFloatingActionMenu.CardAnalysisWidgetFloatingActionMenuListener {
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

        setContentView(R.layout.widget_card_analysis_config)

        cardAnalysisWidgetPreferences = CardAnalysisWidgetPreferences(this)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }

        // Check if the collection is empty before proceeding and if the collection is empty, show a toast instead of the configuration view.
        this.initTask =
            lifecycleScope.launch {
                if (isCollectionEmpty()) {
                    Timber.w("Closing: Collection is empty")
                    showThemedToast(
                        this@CardAnalysisWidgetConfig,
                        R.string.app_not_initialized_new,
                        false,
                    )
                    finish()
                    return@launch
                }

                initializeUIComponents()
                // Don't automatically show deck selection dialog - let user choose when to add decks
            }
    }

    fun showSnackbar(message: CharSequence) {
        showSnackbar(
            message,
            Snackbar.LENGTH_LONG,
        )
    }

    fun showSnackbar(
        @StringRes messageResId: Int,
    ) {
        showSnackbar(getString(messageResId))
    }

    private fun initializeUIComponents() {
        deckAdapter =
            WidgetConfigScreenAdapter { deck, _ ->
                deckAdapter.removeDeck(deck.deckId)
                showSnackbar(R.string.deck_removed_from_widget)
                updateViewVisibility()
                updateSubmitButtonText()
                setUnsavedChanges(true)
            }

        findViewById<RecyclerView>(R.id.recycler_view).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CardAnalysisWidgetConfig.deckAdapter
        }

        setupSaveButton()

        // Initialize floating action menu for deck selection and theming options
        // The menu is automatically set up and handles its own lifecycle
        CardAnalysisWidgetFloatingActionMenu(findViewById(android.R.id.content), this)

        lifecycleScope.launch { updateViewWithSavedPreferences() }

        // Update the visibility of the "no decks" placeholder and the widget configuration container
        updateViewVisibility()

        registerReceiver(widgetRemovedReceiver, IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED))

        onBackPressedCallback =
            object : OnBackPressedCallback(hasUnsavedChanges) {
                override fun handleOnBackPressed() {
                    if (isEnabled) {
                        showDiscardChangesDialog()
                    }
                }
            }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Register the AdapterDataObserver if not already registered
        if (!isAdapterObserverRegistered) {
            deckAdapter.registerAdapterDataObserver(
                object : RecyclerView.AdapterDataObserver() {
                    override fun onChanged() {
                        updateSubmitButtonText()
                    }
                },
            )
            isAdapterObserverRegistered = true
        }
    }

    /**
     * Configures the "Save" button based on the number of selected decks.
     *
     * If no decks are selected: The button is hidden.
     * If decks are selected: The button is visible with the text "Save".
     * When clicked, the selected deck is saved, the widget is updated,
     * and the activity is finished.
     */
    private fun setupSaveButton() {
        val saveButton = findViewById<Button>(R.id.save_button)
        val saveText = getString(R.string.save).uppercase()

        saveButton.text = saveText
        saveButton.setOnClickListener {
            saveSelectedDecksToPreferencesCardAnalysisWidget()
            hasUnsavedChanges = false
            setUnsavedChanges(false)

            val appWidgetManager = AppWidgetManager.getInstance(this)
            CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }

        // Initially set the visibility based on the number of selected decks
        updateSubmitButtonText()
    }

    /** Updates the text of the submit button based on the selected deck count. */
    private fun updateSubmitButtonText() {
        val saveButton = findViewById<Button>(R.id.save_button)
        saveButton.isVisible = deckAdapter.itemCount != 0
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(
            context = this@CardAnalysisWidgetConfig,
            positiveMethod = {
                // Discard changes and finish the activity
                hasUnsavedChanges = false
                finish()
            },
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
        anchorView = findViewById<FloatingActionButton>(R.id.fab_main)
    }

    /** Updates the view according to the saved preference for appWidgetId.*/
    suspend fun updateViewWithSavedPreferences() {
        val selectedDeckId = cardAnalysisWidgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId) ?: return

        val decks = fetchDecks()
        val selectedDecks = decks.filter { it.deckId == selectedDeckId }
        selectedDecks.forEach { deckAdapter.addDeck(it) }
        updateViewVisibility()
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
    private suspend fun fetchDecks(): List<SelectableDeck> =
        withContext(Dispatchers.IO) {
            SelectableDeck.fromCollection(includeFiltered = true)
        }

    /** Displays the deck selection dialog with the provided list of decks. */
    private fun displayDeckSelectionDialog(decks: List<SelectableDeck>) {
        val dialog =
            DeckSelectionDialog.newInstance(
                title = getString(R.string.select_deck_title),
                summaryMessage = null,
                keepRestoreDefaultButton = false,
                decks = decks,
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

        // Check if the deck is already selected
        val isDeckAlreadySelected = deckAdapter.deckIds.contains(deck.deckId)
        if (isDeckAlreadySelected) {
            showSnackbar(getString(R.string.deck_already_selected_message))
            return
        }

        // Check if the deck is being added to a fully occupied selection
        if (deckAdapter.itemCount >= MAX_DECKS_ALLOWED) {
            showSnackbar(resources.getQuantityString(R.plurals.deck_limit_reached, MAX_DECKS_ALLOWED, MAX_DECKS_ALLOWED))
            return
        }
        // Add the deck and update views
        deckAdapter.addDeck(deck)
        updateViewVisibility()
        updateSubmitButtonText()
        setUnsavedChanges(true)

        // Save the selected deck immediately
        saveSelectedDecksToPreferencesCardAnalysisWidget()
        setUnsavedChanges(false)

        // Update the widget with the new selected deck ID
        val appWidgetManager = AppWidgetManager.getInstance(this)
        CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)
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

    /**
     * Shows the theming dialog with single choice options.
     */
    fun showThemingDialog() {
        val currentTheming = cardAnalysisWidgetPreferences.getDynamicThemingPreference(appWidgetId)
        val currentIndex = if (currentTheming) 0 else 1

        androidx.appcompat.app.AlertDialog
            .Builder(this)
            .setTitle(R.string.fab_theming_description)
            .setSingleChoiceItems(
                arrayOf("Dynamic", "AnkiDroid"),
                currentIndex,
            ) { dialog, which ->
                val isDynamic = which == 0
                cardAnalysisWidgetPreferences.saveDynamicThemingPreference(appWidgetId, isDynamic)

                val message =
                    if (isDynamic) {
                        R.string.widget_theming_dynamic_enabled
                    } else {
                        R.string.widget_theming_static_enabled
                    }
                showSnackbar(message)

                // Update widget with new theming
                updateWidgetWithNewTheming()
                dialog.dismiss()
            }.show()
    }

    /**
     * Updates the widget with new theming settings.
     */
    private fun updateWidgetWithNewTheming() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)
    }

    fun saveSelectedDecksToPreferencesCardAnalysisWidget() {
        val selectedDeck = deckAdapter.deckIds.getOrNull(0)
        cardAnalysisWidgetPreferences.saveSelectedDeck(appWidgetId, selectedDeck)

        val updateIntent =
            Intent(this, CardAnalysisWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                putExtra(EXTRA_SELECTED_DECK_IDS, selectedDeck)
            }

        sendBroadcast(updateIntent)
    }

    /** BroadcastReceiver to handle widget removal. */
    private val widgetRemovedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                    return
                }

                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    return
                }

                cardAnalysisWidgetPreferences.deleteDeckData(appWidgetId)
                cardAnalysisWidgetPreferences.deleteDynamicThemingPreference(appWidgetId)
            }
        }

    companion object {
        /**
         * Maximum number of decks allowed in the widget.
         */
        private const val MAX_DECKS_ALLOWED = 1
        private const val EXTRA_SELECTED_DECK_IDS = "card_analysis_widget_selected_deck_ids"
    }

    override fun onAddDeckClicked() {
        showDeckSelectionDialog()
    }

    override fun onThemingClicked() {
        showThemingDialog()
    }
}

fun ContextWrapper.unregisterReceiverSilently(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        Timber.d(e, "unregisterReceiverSilently")
    }
}
