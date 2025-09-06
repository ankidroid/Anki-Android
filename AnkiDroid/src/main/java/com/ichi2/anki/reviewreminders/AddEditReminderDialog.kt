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

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.text.format.DateFormat
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Calendar

class AddEditReminderDialog : DialogFragment() {
    /**
     * Possible states of this dialog.
     * In particular, whether this dialog will be used to add a new review reminder or edit an existing one.
     */
    @Parcelize
    sealed class DialogMode : Parcelable {
        /**
         * Adding a new review reminder. Requires the editing scope of [ScheduleReminders] as an argument so that the dialog can
         * pick a default deck to add to (or, if the scope is global, so that the dialog can
         * show that the review reminder will default to being a global reminder).
         */
        data class Add(
            val schedulerScope: ReviewReminderScope,
        ) : DialogMode()

        /**
         * Editing an existing review reminder. Requires the reminder being edited so that the
         * dialog's fields can be populated with its information.
         */
        data class Edit(
            val reminderToBeEdited: ReviewReminder,
        ) : DialogMode()
    }

    private val viewModel: AddEditReminderDialogViewModel by viewModels {
        viewModelFactory {
            initializer {
                Timber.d("Initializing AddEditReminderDialogViewModel")
                when (val mode = dialogMode) {
                    is DialogMode.Add ->
                        AddEditReminderDialogViewModel(
                            initialTime = getCurrentTime(),
                            initialDeckSelected =
                                when (mode.schedulerScope) {
                                    is ReviewReminderScope.Global -> DeckSpinnerSelection.ALL_DECKS_ID
                                    is ReviewReminderScope.DeckSpecific -> mode.schedulerScope.did
                                },
                            initialCardTriggerThreshold = INITIAL_CARD_THRESHOLD,
                            initialOnlyNotifyIfNoReviews = INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS,
                            initialAdvancedSettingsOpen = INITIAL_ADVANCED_SETTINGS_OPEN,
                        )
                    is DialogMode.Edit ->
                        AddEditReminderDialogViewModel(
                            initialTime = mode.reminderToBeEdited.time,
                            initialDeckSelected =
                                when (mode.reminderToBeEdited.scope) {
                                    is ReviewReminderScope.Global -> DeckSpinnerSelection.ALL_DECKS_ID
                                    is ReviewReminderScope.DeckSpecific -> mode.reminderToBeEdited.scope.did
                                },
                            initialCardTriggerThreshold = mode.reminderToBeEdited.cardTriggerThreshold.threshold,
                            initialOnlyNotifyIfNoReviews = mode.reminderToBeEdited.onlyNotifyIfNoReviews,
                            initialAdvancedSettingsOpen = INITIAL_ADVANCED_SETTINGS_OPEN,
                        )
                }
            }
        }
    }

    private lateinit var contentView: View

    /**
     * The mode of this dialog, retrieved from arguments and set by [getInstance].
     * @see DialogMode
     */
    private val dialogMode: DialogMode by lazy {
        requireNotNull(
            BundleCompat.getParcelable(requireArguments(), DIALOG_MODE_ARGUMENTS_KEY, DialogMode::class.java),
        ) {
            "Dialog mode cannot be null"
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        contentView = layoutInflater.inflate(R.layout.add_edit_reminder_dialog, null)
        Timber.d("dialog mode: %s", dialogMode.toString())

        val dialogBuilder =
            AlertDialog.Builder(requireActivity()).apply {
                customView(contentView)
                positiveButton(R.string.dialog_ok)
                neutralButton(R.string.dialog_cancel)

                if (dialogMode is DialogMode.Edit) {
                    negativeButton(R.string.dialog_positive_delete)
                }
            }
        val dialog = dialogBuilder.create()

        // We cannot create onClickListeners by directly using the lambda argument of positiveButton / negativeButton
        // because setting the onClickListener that way makes the dialog auto-dismiss upon the lambda completing.
        // We may need to abort submission or deletion. Hence we manually set the click listener here and only
        // dismiss conditionally from within the click listener methods (see onSubmit and onDelete).
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setOnClickListener { onSubmit() }
            negativeButton.setOnClickListener { onDelete() }
        }

        Timber.d("Setting up fields")
        setUpToolbar()
        setUpTimeButton()
        setUpDeckSpinner()
        setUpAdvancedDropdown()
        setUpCardThresholdInput()
        setUpOnlyNotifyIfNoReviewsCheckbox()

        // For getting the result of the deck selection sub-dialog from ScheduleReminders
        // See ScheduleReminders.onDeckSelected for more information
        setFragmentResultListener(ScheduleReminders.DECK_SELECTION_RESULT_REQUEST_KEY) { _, bundle ->
            val selectedDeck =
                BundleCompat.getParcelable(
                    bundle,
                    ScheduleReminders.DECK_SELECTION_RESULT_REQUEST_KEY,
                    SelectableDeck::class.java,
                )
            Timber.d("Received result from deck selection sub-dialog: %s", selectedDeck)
            val selectedDeckId: DeckId =
                when (selectedDeck) {
                    is SelectableDeck.Deck -> selectedDeck.deckId
                    is SelectableDeck.AllDecks -> DeckSpinnerSelection.ALL_DECKS_ID
                    else -> Consts.DEFAULT_DECK_ID
                }
            viewModel.setDeckSelected(selectedDeckId)
        }

        dialog.window?.let { resizeWhenSoftInputShown(it) }
        return dialog
    }

    private fun setUpToolbar() {
        val toolbar = contentView.findViewById<Toolbar>(R.id.add_edit_reminder_toolbar)
        toolbar.title =
            when (dialogMode) {
                is DialogMode.Add -> "Add review reminder"
                is DialogMode.Edit -> "Edit review reminder"
            }
    }

    private fun setUpTimeButton() {
        val timeButton = contentView.findViewById<MaterialButton>(R.id.add_edit_reminder_time_button)
        timeButton.setOnClickListener {
            Timber.d("Time button clicked")
            val time = viewModel.time.value ?: getCurrentTime()
            showTimePickerDialog(time.hour, time.minute)
        }
        viewModel.time.observe(this) { time ->
            timeButton.text = time.toString()
        }
    }

    private fun setUpDeckSpinner() {
        val deckSpinner = contentView.findViewById<Spinner>(R.id.add_edit_reminder_deck_spinner)
        val deckSpinnerSelection =
            DeckSpinnerSelection(
                context = (activity as AppCompatActivity),
                spinner = deckSpinner,
                showAllDecks = true,
                alwaysShowDefault = true,
                showFilteredDecks = true,
            )
        launchCatchingTask {
            Timber.d("Setting up deck spinner")
            deckSpinnerSelection.initializeScheduleRemindersDeckSpinner()
            deckSpinnerSelection.selectDeckById(viewModel.deckSelected.value ?: Consts.DEFAULT_DECK_ID, setAsCurrentDeck = false)
        }
    }

    private fun setUpAdvancedDropdown() {
        val advancedDropdown = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_dropdown)
        val advancedDropdownIcon = contentView.findViewById<ImageView>(R.id.add_edit_reminder_advanced_dropdown_icon)
        val advancedContent = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_content)

        advancedDropdown.setOnClickListener {
            viewModel.toggleAdvancedSettingsOpen()
        }
        viewModel.advancedSettingsOpen.observe(this) { advancedSettingsOpen ->
            when (advancedSettingsOpen) {
                true -> {
                    advancedContent.isVisible = true
                    advancedDropdownIcon.setBackgroundResource(DROPDOWN_EXPANDED_CHEVRON)
                }
                false -> {
                    advancedContent.isVisible = false
                    advancedDropdownIcon.setBackgroundResource(DROPDOWN_COLLAPSED_CHEVRON)
                }
            }
        }
    }

    private fun setUpCardThresholdInput() {
        val cardThresholdInput = contentView.findViewById<EditText>(R.id.add_edit_reminder_card_threshold_input)
        cardThresholdInput.setText(viewModel.cardTriggerThreshold.value.toString())
        cardThresholdInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setCardTriggerThreshold(text.toString().toIntOrNull() ?: 0)
        }
    }

    private fun setUpOnlyNotifyIfNoReviewsCheckbox() {
        val contentSection = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_only_notify_if_no_reviews_section)
        val checkbox = contentView.findViewById<MaterialCheckBox>(R.id.add_edit_reminder_only_notify_if_no_reviews_checkbox)
        contentSection.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        checkbox.setOnClickListener {
            viewModel.toggleOnlyNotifyIfNoReviews()
        }
        viewModel.onlyNotifyIfNoReviews.observe(this) { onlyNotifyIfNoReviews ->
            checkbox.isChecked = onlyNotifyIfNoReviews
        }
    }

    /**
     * Show the time picker dialog for selecting a time with a given hour and minute.
     * Does not automatically dismiss the old dialog.
     */
    private fun showTimePickerDialog(
        hour: Int,
        minute: Int,
    ) {
        val dialog =
            MaterialTimePicker
                .Builder()
                .setTheme(R.style.TimePickerStyle)
                .setTimeFormat(if (DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(hour)
                .setMinute(minute)
                .build()
        dialog.addOnPositiveButtonClickListener {
            viewModel.setTime(ReviewReminderTime(dialog.hour, dialog.minute))
        }
        dialog.show(parentFragmentManager, TIME_PICKER_TAG)
    }

    /**
     * For some reason, the TimePicker dialog does not automatically redraw itself properly when the device rotates.
     * Thus, if the TimePicker dialog is active, we manually show a new copy and then dismiss the old one.
     * We need to show the new one before dismissing the old one to ensure there is no annoying flicker.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val previousDialog = parentFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as? MaterialTimePicker
        previousDialog?.let {
            showTimePickerDialog(it.hour, it.minute)
            it.dismiss()
        }
    }

    /**
     * We display the current time as the initial review reminder time when a review reminder is created from scratch.
     */
    private fun getCurrentTime(): ReviewReminderTime {
        val calendarInstance = TimeManager.time.calendar()
        val currentHour = calendarInstance.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendarInstance.get(Calendar.MINUTE)
        return ReviewReminderTime(currentHour, currentMinute)
    }

    private fun onSubmit() {
        Timber.d("Submitted dialog")

        // Validate numerical fields
        if ((viewModel.cardTriggerThreshold.value ?: -1) < 0) {
            contentView.showSnackbar(
                "The card trigger threshold must be a whole number of cards and at least 0",
            )
            return
        }

        val reminderToBeReturned =
            ReviewReminder.createReviewReminder(
                time = viewModel.time.value ?: getCurrentTime(),
                cardTriggerThreshold =
                    ReviewReminderCardTriggerThreshold(
                        threshold = viewModel.cardTriggerThreshold.value ?: INITIAL_CARD_THRESHOLD,
                    ),
                scope =
                    when (viewModel.deckSelected.value) {
                        DeckSpinnerSelection.ALL_DECKS_ID -> ReviewReminderScope.Global
                        else ->
                            ReviewReminderScope.DeckSpecific(
                                did = viewModel.deckSelected.value ?: Consts.DEFAULT_DECK_ID,
                            )
                    },
                enabled =
                    when (val mode = dialogMode) {
                        is DialogMode.Add -> true
                        is DialogMode.Edit -> mode.reminderToBeEdited.enabled
                    },
                onlyNotifyIfNoReviews = viewModel.onlyNotifyIfNoReviews.value ?: INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS,
            )

        Timber.d("Reminder to be returned: %s", reminderToBeReturned)
        setFragmentResult(
            ScheduleReminders.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY,
            Bundle().apply {
                putParcelable(ScheduleReminders.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY, reminderToBeReturned)
            },
        )
        dismiss()
    }

    private fun onDelete() {
        Timber.d("Selected delete reminder button")

        val confirmationDialog = ConfirmationDialog()
        confirmationDialog.setArgs(
            "Delete this reminder?",
            "This action cannot be undone.",
        )
        confirmationDialog.setConfirm {
            setFragmentResult(
                ScheduleReminders.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY,
                Bundle().apply {
                    putParcelable(ScheduleReminders.ADD_EDIT_DIALOG_RESULT_REQUEST_KEY, null)
                },
            )
            dismiss()
        }

        showDialogFragment(confirmationDialog)
    }

    companion object {
        /**
         * Icon that shows next to the advanced settings section when the dropdown is open.
         */
        private val DROPDOWN_EXPANDED_CHEVRON = R.drawable.ic_expand_more_black_24dp_xml

        /**
         * Icon that shows next to the advanced settings section when the dropdown is closed.
         */
        private val DROPDOWN_COLLAPSED_CHEVRON = R.drawable.ic_baseline_chevron_right_24

        /**
         * Arguments key for the dialog mode to open this dialog in.
         * @see DialogMode
         */
        private const val DIALOG_MODE_ARGUMENTS_KEY = "dialog_mode"

        /**
         * Unique fragment tag for the Material TimePicker shown for setting the time of a review reminder.
         */
        private const val TIME_PICKER_TAG = "REMINDER_TIME_PICKER_DIALOG"

        /**
         * The default minimum card trigger threshold that is filled into the dialog when a new review
         * reminder is being created. Since this is set to one, the default behaviour is that users
         * will not get notified about a deck if there are no cards to review for that deck.
         * Users may choose to instead set it to zero, or any other non-negative integer value.
         * This is an Int because that is what the EditText's inputType is.
         */
        private const val INITIAL_CARD_THRESHOLD: Int = 1

        /**
         * The default value for whether a notification should only be fired if no reviews have been done today
         * for the corresponding deck / all decks. Since this is set to false, the default behaviour is that
         * notifications will always be sent, regardless of whether reviews have been done today.
         */
        private const val INITIAL_ONLY_NOTIFY_IF_NO_REVIEWS = false

        /**
         * Whether the advanced settings dropdown is initially open.
         * We start with it closed to avoid overwhelming the user.
         */
        private const val INITIAL_ADVANCED_SETTINGS_OPEN = false

        /**
         * Creates a new instance of this dialog with the given dialog mode.
         */
        fun getInstance(dialogMode: DialogMode): AddEditReminderDialog =
            AddEditReminderDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(DIALOG_MODE_ARGUMENTS_KEY, dialogMode)
                    }
            }
    }
}
