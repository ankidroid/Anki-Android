/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.showError
import com.ichi2.anki.withProgress
import kotlinx.serialization.SerializationException
import timber.log.Timber

/**
 * Fragment for creating, viewing, editing, and deleting review reminders.
 */
class ScheduleReminders :
    Fragment(R.layout.fragment_schedule_reminders),
    DeckSelectionDialog.DeckSelectionListener {
    /**
     * Whether this fragment has been opened to edit all review reminders or just a specific deck's reminders.
     * @see ReviewReminderScope
     */
    private val scheduleRemindersScope: ReviewReminderScope by lazy {
        BundleCompat.getParcelable(
            requireArguments(),
            EXTRAS_SCOPE_KEY,
            ReviewReminderScope::class.java,
        ) ?: ReviewReminderScope.Global
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScheduleRemindersAdapter

    /**
     * The reminders currently being displayed in the UI. To make changes to this list show up on screen,
     * use [triggerUIUpdate]. Note that editing this map does not also automatically write to the database.
     * Writing to the database must be done separately.
     */
    private lateinit var reminders: HashMap<ReviewReminderId, ReviewReminder>

    /**
     * Retrieving deck names for a given deck ID in [setDeckNameFromScopeForView] requires a call to the collection.
     * However, most reminders in the RecyclerView will often be from the same deck (and are guaranteed to be if
     * this fragment is opened in [ReviewReminderScope.DeckSpecific] mode). Hence, we cache deck names.
     */
    private val cachedDeckNames: HashMap<DeckId, String> = hashMapOf()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Set up toolbar
        toolbar = view.findViewById(R.id.toolbar)
        reloadToolbarText()
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        // Set up add button
        val addButton = view.findViewById<ExtendedFloatingActionButton>(R.id.schedule_reminders_add_reminder_fab)
        addButton.setOnClickListener { addReminder() }

        // Set up recycler view
        recyclerView = view.findViewById(R.id.schedule_reminders_recycler_view)
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))

        // Set up adapter, pass functionality to it
        adapter =
            ScheduleRemindersAdapter(
                ::setDeckNameFromScopeForView,
                ::toggleReminderEnabled,
                ::editReminder,
            )
        recyclerView.adapter = adapter

        // Retrieve reminders based on the editing scope
        launchCatchingTask { loadDatabaseRemindersIntoUI() }
    }

    private fun reloadToolbarText() {
        Timber.d("Reloading toolbar text")
        toolbar.title = getString(R.string.schedule_reminders_do_not_translate)
        when (val scope = scheduleRemindersScope) {
            is ReviewReminderScope.Global -> {}
            is ReviewReminderScope.DeckSpecific ->
                launchCatchingTask {
                    toolbar.subtitle = scope.getDeckName()
                }
        }
    }

    /**
     * Fetch all reminders from the database and put them into the RecyclerView.
     */
    private suspend fun loadDatabaseRemindersIntoUI() {
        Timber.d("Loading review reminders from database")
        reminders =
            catchDatabaseExceptions {
                when (val scope = scheduleRemindersScope) {
                    is ReviewReminderScope.Global -> {
                        HashMap(ReviewRemindersDatabase.getAllAppWideReminders() + ReviewRemindersDatabase.getAllDeckSpecificReminders())
                    }
                    is ReviewReminderScope.DeckSpecific -> ReviewRemindersDatabase.getRemindersForDeck(scope.did)
                }
            } ?: hashMapOf()
        triggerUIUpdate()
        Timber.d("Database review reminders successfully loaded")
    }

    /**
     * Sets a TextView's text based on a [ReviewReminderScope].
     * The text is either the scope's associated deck's name, or "All Decks" if the scope is global.
     * For example, this is used to display the [ScheduleRemindersAdapter]'s deck name column.
     */
    private fun setDeckNameFromScopeForView(
        scope: ReviewReminderScope,
        view: TextView,
    ) {
        when (scope) {
            is ReviewReminderScope.Global -> view.text = "All Decks"
            is ReviewReminderScope.DeckSpecific -> {
                launchCatchingTask {
                    val deckName = cachedDeckNames.getOrPut(scope.did) { scope.getDeckName() }
                    view.text = deckName
                }
            }
        }
    }

    /**
     * Toggles whether a review reminder is enabled, i.e. whether its notifications will fire.
     * Saves this information immediately to the database and then updates the UI.
     */
    private fun toggleReminderEnabled(
        id: ReviewReminderId,
        scope: ReviewReminderScope,
    ) {
        Timber.d("Toggling reminder enabled state: %s", id)
        val reminder = reminders[id] ?: return
        val newState = !reminder.enabled

        val performToggle:
            (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder> =
            { reminders ->
                reminders[id]?.enabled = newState
                reminders
            }

        // Update database
        launchCatchingTask {
            catchDatabaseExceptions {
                when (scope) {
                    is ReviewReminderScope.Global -> ReviewRemindersDatabase.editAllAppWideReminders(performToggle)
                    is ReviewReminderScope.DeckSpecific -> ReviewRemindersDatabase.editRemindersForDeck(scope.did, performToggle)
                }
            }
        }

        // Update UI
        reminder.enabled = newState
        triggerUIUpdate()
    }

    /**
     * The method that runs when the "+" icon is pressed, allowing the user to create a new review reminder.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Add] mode.
     */
    private fun addReminder() {
        Timber.d("Adding new review reminder")
    }

    /**
     * The method that runs when an existing reminder is tapped, allowing the user to change its fields.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Edit] mode.
     */
    private fun editReminder(reminder: ReviewReminder) {
        Timber.d("Editing review reminder: %s", reminder.id)
    }

    /**
     * [AddEditReminderDialog] requires a [DeckSelectionDialog.DeckSelectionListener] to catch changes to
     * the [com.ichi2.anki.DeckSpinnerSelection]. However, [AddEditReminderDialog] is removed from the
     * fragment stack when the [DeckSelectionDialog] appears, so we set [ScheduleReminders] as the listener
     * and forward data to [AddEditReminderDialog] when a deck is selected.
     */
    override fun onDeckSelected(deck: SelectableDeck?) {
        Timber.d("Deck selected in deck spinner: %s", deck)
        setFragmentResult(
            DECK_SELECTION_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(DECK_SELECTION_RESULT_REQUEST_KEY, deck)
            },
        )
    }

    /**
     * Trigger a RecyclerView UI update for ScheduleReminders.
     */
    private fun triggerUIUpdate() {
        adapter.submitList(
            reminders
                .values
                .sortedBy { it.time.toSecondsFromMidnight() }
                .toList(),
        )
    }

    companion object {
        /**
         * Arguments key for passing the [ReviewReminderScope] to open this fragment with.
         */
        private const val EXTRAS_SCOPE_KEY = "scope"

        /**
         * Arguments key for storing the current or latest [AddEditReminderDialog] instance.
         * We save this so we can pass [onDeckSelected] onward to the dialog
         * and so we can determine what reminder has been recently edited.
         */
        private const val ACTIVE_DIALOG_MODE_ARGUMENTS_KEY = "active_dialog_mode"

        /**
         * Fragment result key for receiving the result of [AddEditReminderDialog].
         * Public so [AddEditReminderDialog] can access it, too.
         */
        const val ADD_EDIT_DIALOG_RESULT_REQUEST_KEY = "add_edit_reminder_dialog_result_request_key"

        /**
         * Fragment result key for sending [AddEditReminderDialog] the result of the deck spinner selection event.
         * Public so [AddEditReminderDialog] can access it, too.
         * @see onDeckSelected
         */
        const val DECK_SELECTION_RESULT_REQUEST_KEY = "reminder_deck_selection_result_request_key"

        private const val SERIALIZATION_ERROR_MESSAGE =
            "Something went wrong. A serialization error was encountered while working with review reminders."
        private const val DATA_TYPE_ERROR_MESSAGE =
            "Something went wrong. An unexpected data type was found while working with review reminders."

        /**
         * Wrapper for database access.
         * Shows an error dialog if [SerializationException]s or [IllegalArgumentException]s are thrown.
         * Shows a progress dialog if database access takes a long time.
         */
        private suspend fun <T> Fragment.catchDatabaseExceptions(block: () -> T): T? =
            try {
                Timber.d("Attempting ReviewRemindersDatabase operation")
                withProgress { block() }
            } catch (e: SerializationException) {
                Timber.e("JSON Serialization error occurred")
                requireContext().showError("$SERIALIZATION_ERROR_MESSAGE: $e", e.toCrashReportData(requireContext()))
                null
            } catch (e: IllegalArgumentException) {
                Timber.e("JSON Illegal argument exception occurred")
                requireContext().showError("$DATA_TYPE_ERROR_MESSAGE: $e", e.toCrashReportData(requireContext()))
                null
            }

        /**
         * Creates an intent to start the ScheduleReminders fragment.
         * @param context
         * @param scope The editing scope of the ScheduleReminders fragment.
         * @return The new intent.
         */
        fun getIntent(
            context: Context,
            scope: ReviewReminderScope,
        ): Intent =
            SingleFragmentActivity
                .getIntent(
                    context,
                    ScheduleReminders::class,
                    Bundle().apply {
                        putParcelable(EXTRAS_SCOPE_KEY, scope)
                    },
                ).apply {
                    Timber.i("launching ScheduleReminders for %s scope", scope)
                }
    }
}
