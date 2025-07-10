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
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.showError
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.utils.dp
import com.ichi2.utils.updatePaddingRelative
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import timber.log.Timber
import kotlin.reflect.KClass

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

    private lateinit var database: ReviewRemindersDatabase
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ScheduleRemindersAdapter
    private lateinit var columnHeadings: ViewGroup

    private var isInMultiSelectMode: Boolean = false
    private var selectedIdsForBulkDelete: HashSet<ReviewReminderId> = hashSetOf()
    private var selectedIdsForBulkDeleteCount: Int = 0

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

        // Set up back button behaviour
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (isInMultiSelectMode) {
                Timber.d("Back button pressed: disabling multi-select mode")
                disableMultiSelectMode()
            } else {
                // Disable this callback and rethrow the back pressed action
                Timber.d("Back button pressed: returning to previous screen")
                this.remove()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        // Set up menu
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(
                    menu: Menu,
                    menuInflater: MenuInflater,
                ) = menuInflater.inflate(R.menu.schedule_reminders_menu, menu)

                override fun onPrepareMenu(menu: Menu) {
                    Timber.d("Re-creating menu")
                    menu.findItem(R.id.action_add_review_reminder).isVisible = !isInMultiSelectMode
                    menu.findItem(R.id.action_bulk_delete_review_reminder).isVisible = isInMultiSelectMode
                    menu.findItem(R.id.action_select_all).isVisible = (selectedIdsForBulkDeleteCount != adapter.reminders.size)
                    menu.findItem(R.id.action_select_none).isVisible = (selectedIdsForBulkDeleteCount != 0)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = handleMenuItemSelected(menuItem)
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        // Set up recycler view
        recyclerView = view.findViewById(R.id.schedule_reminders_recycler_view)
        val layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))

        // Set up column heading padding
        columnHeadings = view.findViewById(R.id.schedule_reminders_column_headings)
        columnHeadings.updatePaddingRelative(start = SWITCH_WIDTH.dp)

        // Set up database
        database = ReviewRemindersDatabase()

        // Set up adapter
        adapter =
            ScheduleRemindersAdapter(
                mutableListOf(),
                // Pass functionality to the adapter
                ::setDeckNameFromScopeForView,
                ::toggleReminderEnabled,
                ::enableMultiSelectMode,
                ::toggleReminderSelected,
                ::isInMultiSelectMode,
                ::isReminderSelected,
                ::editReminder,
            )
        recyclerView.adapter = adapter

        // Retrieve reminders based on the editing scope
        launchCatchingTask { loadDatabaseRemindersIntoUI() }

        // If the user creates or edits a review reminder, the dialog for doing so opens
        // Once their changes are complete, the dialog closes and this fragment is reloaded
        // Hence, we check for any fragment results here and update the database accordingly
        setFragmentResultListener(ADD_EDIT_DIALOG_RESULT_REQUEST_KEY) { _, bundle ->
            val modeOfFinishedDialog =
                BundleCompat.getParcelable(
                    requireArguments(),
                    ACTIVE_DIALOG_MODE_ARGUMENTS_KEY,
                    AddEditReminderDialog.DialogMode::class.java,
                )
            val newOrModifiedReminder = BundleCompat.getParcelable(bundle, ADD_EDIT_DIALOG_RESULT_REQUEST_KEY, ReviewReminder::class.java)

            Timber.d("Dialog result received with recent dialog mode: $modeOfFinishedDialog")
            if (modeOfFinishedDialog == null) return@setFragmentResultListener
            handleAddEditDialogResult(newOrModifiedReminder, modeOfFinishedDialog)
        }
    }

    private fun handleMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            R.id.action_add_review_reminder -> {
                addReminder()
                true
            }
            R.id.action_bulk_delete_review_reminder -> {
                bulkDeleteSelectedReminders()
                true
            }
            R.id.action_select_all -> {
                selectAllReminders()
                true
            }
            R.id.action_select_none -> {
                disableMultiSelectMode()
                true
            }
            else -> false
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
        val remindersAsMap =
            catchDatabaseExceptions {
                when (val scope = scheduleRemindersScope) {
                    is ReviewReminderScope.Global -> {
                        (database.getAllAppWideReminders() + database.getAllDeckSpecificReminders())
                    }
                    is ReviewReminderScope.DeckSpecific -> database.getRemindersForDeck(scope.did)
                }
            } ?: hashMapOf()
        val remindersAsList = remindersAsMap.values.sortedBy { it.time.toSecondsFromMidnight() }
        adapter.reminders.addAll(remindersAsList)
        adapter.notifyDataSetChanged()
        Timber.d("Database review reminders successfully loaded")
    }

    /**
     * When a [AddEditReminderDialog] instance finishes, we handle the result of the dialog fragment via this method.
     */
    private fun handleAddEditDialogResult(
        newOrModifiedReminder: ReviewReminder?,
        modeOfFinishedDialog: AddEditReminderDialog.DialogMode,
    ) {
        Timber.d("Handling add/edit dialog result: mode=$modeOfFinishedDialog reminder=$newOrModifiedReminder")
        // Modify database
        launchCatchingTask {
            catchDatabaseExceptions {
                if (modeOfFinishedDialog is AddEditReminderDialog.DialogMode.Edit) {
                    // Delete the existing reminder if we're in edit mode
                    // This action must be separated from writing the modified reminder because the user may have updated the reminder's deck,
                    // meaning we need to delete the old reminder in the old deck, then add a new reminder to the new deck
                    val reminderToDelete = modeOfFinishedDialog.reminderToBeEdited
                    Timber.d("Deleting old reminder from database")
                    val deleteReminderWithId: (
                        HashMap<ReviewReminderId, ReviewReminder>,
                    ) -> Map<ReviewReminderId, ReviewReminder> = { reminders ->
                        reminders.remove(reminderToDelete.id)
                        reminders
                    }
                    when (reminderToDelete.scope) {
                        is ReviewReminderScope.Global -> database.editAllAppWideReminders(deleteReminderWithId)
                        is ReviewReminderScope.DeckSpecific ->
                            database.editRemindersForDeck(
                                reminderToDelete.scope.did,
                                deleteReminderWithId,
                            )
                    }
                }

                newOrModifiedReminder?.let {
                    Timber.d("Writing new or modified reminder to database")
                    // There is a new / updated reminder that must be written to the database
                    val writeNewOrModifiedReminder: (
                        HashMap<ReviewReminderId, ReviewReminder>,
                    ) -> Map<ReviewReminderId, ReviewReminder> = { reminders ->
                        reminders[it.id] = it
                        reminders
                    }
                    when (it.scope) {
                        is ReviewReminderScope.Global -> database.editAllAppWideReminders(writeNewOrModifiedReminder)
                        is ReviewReminderScope.DeckSpecific -> database.editRemindersForDeck(it.scope.did, writeNewOrModifiedReminder)
                    }
                }
            }
        }

        // Modify UI
        if (modeOfFinishedDialog is AddEditReminderDialog.DialogMode.Edit) {
            Timber.d("Deleting old reminder from UI")
            adapter.reminders.remove(modeOfFinishedDialog.reminderToBeEdited)
        }
        newOrModifiedReminder?.let {
            if (scheduleRemindersScope == ReviewReminderScope.Global || scheduleRemindersScope == it.scope) {
                Timber.d("Adding new reminder to UI")
                adapter.reminders.add(it)
            }
            // Sort the reminders by the chosen sort order
            adapter.reminders.sortBy { eachReminder -> eachReminder.time.toSecondsFromMidnight() }
        }
        adapter.notifyDataSetChanged()

        showSnackbar(
            when (modeOfFinishedDialog) {
                is AddEditReminderDialog.DialogMode.Add -> "Successfully added new review reminder"
                is AddEditReminderDialog.DialogMode.Edit -> {
                    when (newOrModifiedReminder) {
                        null -> "Successfully deleted review reminder"
                        else -> "Successfully edited review reminder"
                    }
                }
            },
        )

        // If we change from no reminders showing to one reminder showing, or vice versa,
        // we need to change the visibility of the toolbar "Select all" button
        // Hence we reload the toolbar here
        requireActivity().invalidateOptionsMenu()
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
     * Saves this information immediately to SharedPreferences and then updates the UI.
     */
    private fun toggleReminderEnabled(
        id: ReviewReminderId,
        scope: ReviewReminderScope,
        position: Int,
    ) {
        Timber.d("Toggling reminder enabled state: $id")
        val newState = !adapter.reminders[position].enabled

        val performToggle:
            (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder> =
            { reminders ->
                reminders[id]?.enabled = newState
                reminders
            }

        launchCatchingTask {
            catchDatabaseExceptions {
                when (scope) {
                    is ReviewReminderScope.Global -> database.editAllAppWideReminders(performToggle)
                    is ReviewReminderScope.DeckSpecific -> database.editRemindersForDeck(scope.did, performToggle)
                }
            }
        }

        adapter.reminders[position].enabled = newState
        adapter.notifyItemChanged(position)
    }

    /**
     * The method that runs when the "+" icon is pressed, allowing the user to create a new review reminder.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Add] mode.
     */
    private fun addReminder() {
        Timber.d("Adding new review reminder")
        val dialogMode = AddEditReminderDialog.DialogMode.Add(scheduleRemindersScope)
        val dialog = AddEditReminderDialog.getInstance(dialogMode)
        // Save the dialog mode so that we refer back to it once the dialog closes
        requireArguments().putParcelable(ACTIVE_DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
        showDialogFragment(dialog)
    }

    /**
     * The method that runs when an existing reminder is tapped, allowing the user to change its fields.
     * Opens [AddEditReminderDialog] in [AddEditReminderDialog.DialogMode.Edit] mode.
     */
    private fun editReminder(reminder: ReviewReminder) {
        Timber.d("Editing review reminder: ${reminder.id}")
        val dialogMode = AddEditReminderDialog.DialogMode.Edit(reminder)
        val dialog = AddEditReminderDialog.getInstance(dialogMode)
        // Save the dialog mode so that we refer back to it once the dialog closes
        requireArguments().putParcelable(ACTIVE_DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
        showDialogFragment(dialog)
    }

    /**
     * In multi-select mode, clicking the delete icon will trigger this method.
     * Deletes every reminder which has its ID in the [selectedIdsForBulkDelete] list.
     * Shows a confirmation dialog before doing so.
     */
    private fun bulkDeleteSelectedReminders() {
        Timber.d("Bulk deleting selected reminders")

        val confirmationDialog = ConfirmationDialog()
        confirmationDialog.setArgs(
            "Delete these $selectedIdsForBulkDeleteCount reminders?",
            "This action cannot be undone.",
        )

        val filterOutDeletedIdsAndDisplayRemaining:
            (HashMap<ReviewReminderId, ReviewReminder>) -> Map<ReviewReminderId, ReviewReminder> =
            { reminders ->
                selectedIdsForBulkDelete.forEach { reminders.remove(it) }
                adapter.reminders.addAll(reminders.values.toMutableList())
                reminders
            }

        confirmationDialog.setConfirm {
            // Clear the existing displayed reminders in the UI
            adapter.reminders.clear()
            // Perform filtering
            launchCatchingTask {
                catchDatabaseExceptions {
                    when (val scope = scheduleRemindersScope) {
                        is ReviewReminderScope.Global -> {
                            database.editAllAppWideReminders(filterOutDeletedIdsAndDisplayRemaining)
                            database.editAllDeckSpecificReminders(filterOutDeletedIdsAndDisplayRemaining)
                        }
                        is ReviewReminderScope.DeckSpecific -> {
                            database.editRemindersForDeck(scope.did, filterOutDeletedIdsAndDisplayRemaining)
                        }
                    }
                }
            }

            // Update UI
            // No need to re-sort the reminders because deletions don't affect sort order
            adapter.notifyDataSetChanged()
            disableMultiSelectMode()
        }

        showDialogFragment(confirmationDialog)
    }

    /**
     * Enables multi-select mode, which then allows for bulk deletion of reminders.
     * Updates the UI to show checkboxes to the left of each reminder in the RecyclerView.
     */
    private fun enableMultiSelectMode() {
        Timber.d("Enabling multi-select mode")
        isInMultiSelectMode = true

        toolbar.title = selectedIdsForBulkDeleteCount.toString()
        toolbar.subtitle = ""

        // Adding a checkbox to each row, adding corresponding padding
        columnHeadings.updatePaddingRelative(start = (SWITCH_WIDTH + CHECK_BOX_WIDTH).dp)
        adapter.notifyDataSetChanged()

        // Update toolbar icons
        requireActivity().invalidateOptionsMenu()
    }

    /**
     * Disables multi-select mode.
     * Updates the UI to remove the checkboxes to the left of each reminder in the RecyclerView.
     */
    private fun disableMultiSelectMode() {
        Timber.d("Disabling multi-select mode")
        isInMultiSelectMode = false

        selectedIdsForBulkDelete.clear()
        selectedIdsForBulkDeleteCount = 0

        reloadToolbarText()

        // Remove padding in headings that made room for checkboxes
        columnHeadings.updatePaddingRelative(start = SWITCH_WIDTH.dp)
        adapter.notifyDataSetChanged()

        // Update toolbar icons
        requireActivity().invalidateOptionsMenu()
    }

    /**
     * Toggles whether a reminder is selected for bulk deletion.
     */
    private fun toggleReminderSelected(
        id: ReviewReminderId,
        position: Int,
    ) {
        Timber.d("Selected reminder for bulk deletion: $id")
        if (id in selectedIdsForBulkDelete) {
            selectedIdsForBulkDelete.remove(id)
            selectedIdsForBulkDeleteCount--
        } else {
            selectedIdsForBulkDelete.add(id)
            selectedIdsForBulkDeleteCount++
        }
        toolbar.title = selectedIdsForBulkDeleteCount.toString()
        adapter.notifyItemChanged(position)
    }

    /**
     * Selects all review reminders for bulk deletion and ensures multi-select mode is enabled.
     */
    private fun selectAllReminders() {
        Timber.d("Selected all reminders for bulk deletion")
        selectedIdsForBulkDelete = adapter.reminders.map { it.id }.toHashSet()
        selectedIdsForBulkDeleteCount = selectedIdsForBulkDelete.size
        enableMultiSelectMode()
    }

    /**
     * Indicator of whether a reminder is currently slated for bulk deletion.
     * Always false when multi-select mode is not enabled.
     */
    private fun isReminderSelected(id: ReviewReminderId): Boolean = id in selectedIdsForBulkDelete

    /**
     * [AddEditReminderDialog] requires a [DeckSelectionDialog.DeckSelectionListener] to catch changes to
     * the [com.ichi2.anki.DeckSpinnerSelection]. However, [AddEditReminderDialog] is removed from the
     * fragment stack when the [DeckSelectionDialog] appears, so we set [ScheduleReminders] as the listener
     * and forward data to [AddEditReminderDialog] when a deck is selected.
     */
    override fun onDeckSelected(deck: DeckSelectionDialog.SelectableDeck?) {
        Timber.d("Deck selected in deck spinner: $deck")
        setFragmentResult(
            DECK_SELECTION_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(DECK_SELECTION_RESULT_REQUEST_KEY, deck)
            },
        )
    }

    /**
     * Handles and tries to recover from possible [SerializationException]s and [IllegalArgumentException]s that
     * can be thrown by [ReviewRemindersDatabase] methods. Should wrap every call to [ReviewRemindersDatabase] methods.
     *
     * To learn more about the schema migration fallback, see [ReviewReminder]. This method will try every possible
     * old [ReviewReminder] schema in [oldReviewReminderSchemasForMigration] until one succeeds or all fail,
     * reverting any changes made to review reminder SharedPreferences to their original state after each failure.
     *
     * We need to opt into an experimental serialization API feature because we are determining classes to deserialize
     * dynamically rather than at compile-time. The possible schemas to deserialize from are inputted dynamically so that unit tests are possible.
     */
    @OptIn(InternalSerializationApi::class)
    private suspend fun <T> catchDatabaseExceptions(block: () -> T): T? {
        val remainingMigrationSchema = oldReviewReminderSchemasForMigration.toMutableList()
        var nextSchemaToTry: KClass<out OldReviewReminderSchema>? = null
        val sharedPrefsBackup = database.getAllReviewReminderSharedPrefsAsMap()

        val restoreAndPossiblyTryNextSchema: (String, Exception) -> Boolean = { errorMessage, e ->
            Timber.d("Json error encountered, reverting review reminder SharedPreferences")
            database.deleteAllReviewReminderSharedPrefs()
            AnkiDroidApp.sharedPrefs().edit {
                sharedPrefsBackup.forEach { (key, value) ->
                    putString(key, value.toString())
                }
            }
            if (remainingMigrationSchema.isNotEmpty()) {
                nextSchemaToTry = remainingMigrationSchema.removeAt(remainingMigrationSchema.lastIndex)
                Timber.d("Attempting ReviewReminder schema migration: $nextSchemaToTry")
                true
            } else {
                Timber.d("No more ReviewReminder migration schemas are available, showing error dialog")
                showError(requireContext(), "$errorMessage $e")
                false
            }
        }

        while (true) {
            try {
                Timber.d("Attempting ReviewRemindersDatabase operation")
                val result =
                    withProgress {
                        nextSchemaToTry?.let { database.attemptSchemaMigration(it.serializer()) }
                        block()
                    }
                return result
            } catch (e: SerializationException) {
                if (restoreAndPossiblyTryNextSchema(SERIALIZATION_ERROR_MESSAGE, e)) continue else return null
            } catch (e: IllegalArgumentException) {
                if (restoreAndPossiblyTryNextSchema(DATA_TYPE_ERROR_MESSAGE, e)) continue else return null
            }
        }
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
        const val ADD_EDIT_DIALOG_RESULT_REQUEST_KEY = "add_edit_dialog_result_request_key"

        /**
         * Fragment result key for sending [AddEditReminderDialog] the result of the deck spinner selection event.
         * Public so [AddEditReminderDialog] can access it, too.
         * @see onDeckSelected
         */
        const val DECK_SELECTION_RESULT_REQUEST_KEY = "deck_selection_result_request_key"

        /**
         * For properly adjusting the horizontal padding of the column headings when multi-select mode is entered.
         */
        private const val CHECK_BOX_WIDTH = 48

        /**
         * For properly adjusting the horizontal padding of the column headings when multi-select mode is entered.
         * Public so that the adapter can also access and use it for setting the switch widths.
         */
        const val SWITCH_WIDTH = 96

        private const val SERIALIZATION_ERROR_MESSAGE =
            "Something went wrong. A serialization error was encountered while working with review reminders."
        private const val DATA_TYPE_ERROR_MESSAGE =
            "Something went wrong. An unexpected data type was found while working with review reminders."

        /**
         * A list of all old [ReviewReminder] schemas that [ScheduleReminders.catchDatabaseExceptions]
         * will attempt to migrate old review reminders in SharedPreferences from.
         * @see [ReviewReminder]
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        var oldReviewReminderSchemasForMigration: List<KClass<out OldReviewReminderSchema>> = listOf()

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
                    Timber.i("launching ScheduleReminders for $scope scope")
                }
    }
}
