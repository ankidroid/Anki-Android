/*
   Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

This program is free software; you can redistribute it and/or modify it under
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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeckPickerWidgetConfig : FragmentActivity(), DeckSelectionListener {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var deckAdapter: DeckPickerWidgetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.widget_deck_picker_config)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewSelectedDecks)
        recyclerView.layoutManager = LinearLayoutManager(this)
        deckAdapter = DeckPickerWidgetAdapter(mutableListOf())
        recyclerView.adapter = deckAdapter

        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        findViewById<Button>(R.id.done_button).setOnClickListener {
            saveSelectedDecksToPreferences()

            val selectedDeckIds = getSelectedDeckIds().toLongArray()

            val appWidgetManager = AppWidgetManager.getInstance(this)
            DeckPickerWidget.updateWidget(this, appWidgetManager, intArrayOf(appWidgetId), selectedDeckIds)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)

            // Send broadcast to update widget
            sendBroadcast(Intent(this, DeckPickerWidget::class.java))

            finish()
        }

        findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker).setOnClickListener {
            showDeckSelectionDialog()
        }

        loadSelectedDecksFromPreferences()
        updateViewVisibility()
    }

    private fun showDeckSelectionDialog() {
        lifecycleScope.launch {
            val decks = fetchDecks()
            displayDeckSelectionDialog(decks)
        }
    }

    private suspend fun fetchDecks(): List<SelectableDeck> {
        return withContext(Dispatchers.IO) {
            SelectableDeck.fromCollection(includeFiltered = false)
        }
    }

    private fun displayDeckSelectionDialog(decks: List<SelectableDeck>) {
        val dialog = DeckSelectionDialog.newInstance(
            title = getString(R.string.select_deck_title),
            summaryMessage = null,
            keepRestoreDefaultButton = false,
            decks = decks
        )
        dialog.show(supportFragmentManager, "DeckSelectionDialog")
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        if (deck != null) {
            if (deckAdapter.itemCount >= 5) {
                showSnackbar(getString(R.string.deck_limit_reached))
            } else {
                deckAdapter.addDeck(deck)
                updateViewVisibility()
            }
        }
    }

    private fun updateViewVisibility() {
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

    private fun saveSelectedDecksToPreferences() {
        val sharedPreferences = getSharedPreferences("DeckPickerWidgetPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val selectedDecks = deckAdapter.decks.map { it.deckId.toString() }.toSet()
        editor.putStringSet("selected_decks_$appWidgetId", selectedDecks)
        editor.apply()

        // Trigger widget update
        val updateIntent = Intent(this, DeckPickerWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            putExtra("selected_deck_ids", selectedDecks.map { it.toLong() }.toLongArray())
        }
        sendBroadcast(updateIntent)
    }

    private fun loadSelectedDecksFromPreferences() {
        val sharedPreferences = getSharedPreferences("DeckPickerWidgetPrefs", Context.MODE_PRIVATE)
        val selectedDecks = sharedPreferences.getStringSet("selected_decks_$appWidgetId", emptySet())
        if (!selectedDecks.isNullOrEmpty()) {
            lifecycleScope.launch {
                val decks = fetchDecks()
                val selectedDeckObjects = decks.filter { selectedDecks.contains(it.deckId.toString()) }
                deckAdapter.setDecks(selectedDeckObjects)
                updateViewVisibility()
            }
        }
    }

    private fun getSelectedDeckIds(): List<Long> {
        return deckAdapter.decks.map { it.deckId }
    }
}
