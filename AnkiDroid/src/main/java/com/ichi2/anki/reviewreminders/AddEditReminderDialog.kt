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
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
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
import com.ichi2.anki.DeckSpinnerSelection
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

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
                            INITIAL_TIME,
                            initialDeckRadioSelected =
                                when (mode.schedulerScope) {
                                    is ReviewReminderScope.Global -> AddEditReminderDialogViewModel.DeckRadioOptions.GLOBAL
                                    is ReviewReminderScope.DeckSpecific -> AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC
                                },
                            initialDeckSelected =
                                when (mode.schedulerScope) {
                                    is ReviewReminderScope.Global -> Consts.DEFAULT_DECK_ID
                                    is ReviewReminderScope.DeckSpecific -> mode.schedulerScope.did
                                },
                            INITIAL_SNOOZE_RADIO,
                            INITIAL_SNOOZE_INTERVAL_IN_MINUTES,
                            INITIAL_MAX_SNOOZES,
                            INITIAL_CARD_THRESHOLD,
                            INITIAL_ADVANCED_SETTINGS_OPEN,
                        )
                    is DialogMode.Edit ->
                        AddEditReminderDialogViewModel(
                            mode.reminderToBeEdited.time,
                            initialDeckRadioSelected =
                                when (mode.reminderToBeEdited.scope) {
                                    is ReviewReminderScope.Global -> AddEditReminderDialogViewModel.DeckRadioOptions.GLOBAL
                                    is ReviewReminderScope.DeckSpecific -> AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC
                                },
                            initialDeckSelected =
                                when (mode.reminderToBeEdited.scope) {
                                    is ReviewReminderScope.Global -> Consts.DEFAULT_DECK_ID
                                    is ReviewReminderScope.DeckSpecific -> mode.reminderToBeEdited.scope.did
                                },
                            initialSnoozeRadioSelected =
                                when (mode.reminderToBeEdited.snoozeAmount) {
                                    is ReviewReminderSnoozeAmount.Disabled -> AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED
                                    is ReviewReminderSnoozeAmount.Infinite -> AddEditReminderDialogViewModel.SnoozeRadioOptions.INFINITE
                                    is ReviewReminderSnoozeAmount.SetAmount -> AddEditReminderDialogViewModel.SnoozeRadioOptions.SET_AMOUNT
                                },
                            initialSnoozeInterval =
                                when (mode.reminderToBeEdited.snoozeAmount) {
                                    is ReviewReminderSnoozeAmount.Disabled -> INITIAL_SNOOZE_INTERVAL_IN_MINUTES
                                    is ReviewReminderSnoozeAmount.Infinite ->
                                        mode.reminderToBeEdited.snoozeAmount.interval.inWholeMinutes
                                            .toInt()
                                    is ReviewReminderSnoozeAmount.SetAmount ->
                                        mode.reminderToBeEdited.snoozeAmount.interval.inWholeMinutes
                                            .toInt()
                                },
                            initialMaxSnoozes =
                                when (mode.reminderToBeEdited.snoozeAmount) {
                                    is ReviewReminderSnoozeAmount.Disabled -> INITIAL_MAX_SNOOZES
                                    is ReviewReminderSnoozeAmount.Infinite -> INITIAL_MAX_SNOOZES
                                    is ReviewReminderSnoozeAmount.SetAmount -> mode.reminderToBeEdited.snoozeAmount.maxSnoozes
                                },
                            mode.reminderToBeEdited.cardTriggerThreshold.threshold,
                            INITIAL_ADVANCED_SETTINGS_OPEN,
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

        // We cannot directly use the lambda argument of positiveButton / negativeButton because we may need to abort submission or deletion
        // Hence we manually set the click listener here and only dismiss conditionally from within the click listener methods
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setOnClickListener { onSubmit() }
            negativeButton.setOnClickListener { onDelete() }
        }
        setUpDialogFields()

        // For getting the result of the time-picker sub-dialog
        setFragmentResultListener(TIME_FRAGMENT_RESULT_REQUEST_KEY) { _, bundle ->
            val newTime =
                BundleCompat.getParcelable(
                    bundle,
                    TIME_FRAGMENT_RESULT_REQUEST_KEY,
                    ReviewReminderTime::class.java,
                )
            Timber.d("Received result from time-picker sub-dialog: $newTime")
            newTime?.let { viewModel.setTime(it) }
        }

        // For getting the result of the deck selection sub-dialog from ScheduleReminders
        // See ScheduleReminders.onDeckSelected for more information
        setFragmentResultListener(ScheduleReminders.DECK_SELECTION_RESULT_REQUEST_KEY) { _, bundle ->
            val selectedDeck =
                BundleCompat.getParcelable(
                    bundle,
                    ScheduleReminders.DECK_SELECTION_RESULT_REQUEST_KEY,
                    DeckSelectionDialog.SelectableDeck::class.java,
                )
            Timber.d("Received result from deck selection sub-dialog: $selectedDeck")
            viewModel.setDeckSelected(selectedDeck?.deckId ?: Consts.DEFAULT_DECK_ID)
        }

        dialog.window?.let { resizeWhenSoftInputShown(it) }
        return dialog
    }

    fun setUpDialogFields() {
        Timber.d("Setting up fields")

        // Retrieve view references
        val toolbar = contentView.findViewById<Toolbar>(R.id.add_edit_reminder_toolbar)

        val timeButton = contentView.findViewById<MaterialButton>(R.id.add_edit_reminder_time_button)

        val deckRadioGroup = contentView.findViewById<RadioGroup>(R.id.add_edit_reminder_deck_radio_group)
        val deckSpinnerContainer = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_deck_spinner_container)
        val deckSpinner = contentView.findViewById<Spinner>(R.id.add_edit_reminder_deck_spinner)

        val snoozeRadioGroup = contentView.findViewById<RadioGroup>(R.id.add_edit_reminder_snooze_radio_group)
        val snoozeAmountInput = contentView.findViewById<EditText>(R.id.add_edit_reminder_snooze_amount_input)
        val snoozeIntervalSection = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_snooze_interval_section)
        val snoozeIntervalInput = contentView.findViewById<EditText>(R.id.add_edit_reminder_snooze_interval_input)

        val advancedDropdown = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_dropdown)
        val advancedDropdownIcon = contentView.findViewById<ImageView>(R.id.add_edit_reminder_advanced_dropdown_icon)
        val advancedContent = contentView.findViewById<LinearLayout>(R.id.add_edit_reminder_advanced_content)

        val cardThresholdInput = contentView.findViewById<EditText>(R.id.add_edit_reminder_card_threshold_input)

        // Set up toolbar
        toolbar.title =
            when (dialogMode) {
                is DialogMode.Add -> "Add review reminder"
                is DialogMode.Edit -> "Edit review reminder"
            }

        // Set up time button
        timeButton.setOnClickListener {
            Timber.d("Time button clicked")
            val dialog = TimePickerDialog.getInstance(viewModel.time.value ?: INITIAL_TIME)
            showDialogFragment(dialog)
        }

        // Set up deck spinner
        // We provide a separate "All decks" radio option, so there's no need to show it in the deck selection spinner
        val deckSpinnerSelection =
            DeckSpinnerSelection(
                context = (activity as AppCompatActivity),
                spinner = deckSpinner,
                showAllDecks = false,
                alwaysShowDefault = true,
                showFilteredDecks = true,
            )
        launchCatchingTask {
            Timber.d("Setting up deck spinner")
            deckSpinnerSelection.initializeScheduleRemindersDeckSpinner()
            deckSpinnerSelection.selectDeckById(viewModel.deckSelected.value ?: Consts.DEFAULT_DECK_ID, false)
        }

        // Fill in initial field values
        timeButton.text = viewModel.time.value.toString()
        when (viewModel.deckRadioSelected.value) {
            AddEditReminderDialogViewModel.DeckRadioOptions.GLOBAL ->
                deckRadioGroup.check(
                    R.id.add_edit_reminder_deck_radio_button_all_decks,
                )
            AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC ->
                deckRadioGroup.check(
                    R.id.add_edit_reminder_deck_radio_button_specific_deck,
                )
            null -> deckRadioGroup.check(R.id.add_edit_reminder_deck_radio_button_all_decks)
        }
        when (viewModel.snoozeRadioSelected.value) {
            AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED ->
                snoozeRadioGroup.check(
                    R.id.add_edit_reminder_snooze_radio_button_disabled,
                )
            AddEditReminderDialogViewModel.SnoozeRadioOptions.INFINITE ->
                snoozeRadioGroup.check(
                    R.id.add_edit_reminder_snooze_radio_button_forever,
                )
            AddEditReminderDialogViewModel.SnoozeRadioOptions.SET_AMOUNT ->
                snoozeRadioGroup.check(
                    R.id.add_edit_reminder_snooze_radio_button_set_amount,
                )
            null -> snoozeRadioGroup.check(R.id.add_edit_reminder_snooze_radio_button_disabled)
        }
        snoozeIntervalInput.setText(viewModel.snoozeInterval.value.toString())
        snoozeAmountInput.setText(viewModel.maxSnoozes.value.toString())
        cardThresholdInput.setText(viewModel.cardTriggerThreshold.value.toString())

        // 1. Define how the viewModel's stored state changes as the user edits the fields of the dialog
        deckRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.add_edit_reminder_deck_radio_button_all_decks -> {
                    viewModel.setDeckRadioSelected(AddEditReminderDialogViewModel.DeckRadioOptions.GLOBAL)
                }
                R.id.add_edit_reminder_deck_radio_button_specific_deck -> {
                    viewModel.setDeckRadioSelected(AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC)
                }
            }
        }
        snoozeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.add_edit_reminder_snooze_radio_button_disabled -> {
                    viewModel.setSnoozeRadioSelected(AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED)
                }
                R.id.add_edit_reminder_snooze_radio_button_forever -> {
                    viewModel.setSnoozeRadioSelected(AddEditReminderDialogViewModel.SnoozeRadioOptions.INFINITE)
                }
                R.id.add_edit_reminder_snooze_radio_button_set_amount -> {
                    viewModel.setSnoozeRadioSelected(AddEditReminderDialogViewModel.SnoozeRadioOptions.SET_AMOUNT)
                }
            }
        }
        snoozeIntervalInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setSnoozeInterval(text.toString().toIntOrNull())
        }
        snoozeAmountInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setMaxSnoozes(text.toString().toIntOrNull())
        }
        cardThresholdInput.doOnTextChanged { text, _, _, _ ->
            viewModel.setCardTriggerThreshold(text.toString().toIntOrNull())
        }
        advancedDropdown.setOnClickListener {
            viewModel.toggleAdvancedSettingsOpen()
        }

        // 2. Configure how the UI reacts to changes in the state
        // We hide certain fields if they are extraneous information that does not need to be shown to the user currently
        // For example, there's no need to ask the user how long the review reminder's snooze interval should be if snooze is disabled
        viewModel.time.observe(this) { time ->
            timeButton.text = time.toString()
        }
        viewModel.deckRadioSelected.observe(this) { selected ->
            // For some reason toggling the visibility of the deck spinner itself doesn't work, so we toggle the visibility of its container
            deckSpinnerContainer.isVisible = (selected == AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC)
        }
        viewModel.snoozeRadioSelected.observe(this) { selected ->
            snoozeIntervalSection.isVisible = (selected != AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED)
            snoozeAmountInput.isVisible = (selected == AddEditReminderDialogViewModel.SnoozeRadioOptions.SET_AMOUNT)
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

    private fun onSubmit() {
        Timber.d("Submitted dialog")

        // Validate numerical fields
        // TODO The UX of the snoozeInterval field and the readability of this code for its validation is terrible
        // TODO Get rid of the magic number constant, use a time picker here instead of an EditText, and make validation more robust
        val currentSnoozeIntervalValue = viewModel.snoozeInterval.value
        if (currentSnoozeIntervalValue == null || currentSnoozeIntervalValue < 1 || currentSnoozeIntervalValue >= (24 * 60)) {
            contentView.showSnackbar(
                "The snooze interval must be at least 1 minute and must be less than 24 hours",
            )
            return
        }
        if ((viewModel.maxSnoozes.value ?: 0) < 1) {
            contentView.showSnackbar(
                "The maximum number of snoozes must be a whole number and at least 1",
            )
            return
        }
        if ((viewModel.cardTriggerThreshold.value ?: -1) < 0) {
            contentView.showSnackbar(
                "The card trigger threshold must be a whole number of cards and at least 0",
            )
            return
        }

        val reminderToBeReturned =
            ReviewReminder.createReviewReminder(
                time = viewModel.time.value ?: INITIAL_TIME,
                snoozeAmount =
                    when (viewModel.snoozeRadioSelected.value) {
                        AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED -> ReviewReminderSnoozeAmount.Disabled
                        AddEditReminderDialogViewModel.SnoozeRadioOptions.INFINITE ->
                            ReviewReminderSnoozeAmount.Infinite(
                                interval = viewModel.snoozeInterval.value?.minutes ?: INITIAL_SNOOZE_INTERVAL_IN_MINUTES.minutes,
                            )
                        AddEditReminderDialogViewModel.SnoozeRadioOptions.SET_AMOUNT ->
                            ReviewReminderSnoozeAmount.SetAmount(
                                interval = viewModel.snoozeInterval.value?.minutes ?: INITIAL_SNOOZE_INTERVAL_IN_MINUTES.minutes,
                                maxSnoozes = viewModel.maxSnoozes.value ?: INITIAL_MAX_SNOOZES,
                            )
                        null -> ReviewReminderSnoozeAmount.Disabled
                    },
                cardTriggerThreshold =
                    ReviewReminderCardTriggerThreshold(
                        threshold = viewModel.cardTriggerThreshold.value ?: INITIAL_CARD_THRESHOLD,
                    ),
                scope =
                    when (viewModel.deckRadioSelected.value) {
                        AddEditReminderDialogViewModel.DeckRadioOptions.GLOBAL -> ReviewReminderScope.Global
                        AddEditReminderDialogViewModel.DeckRadioOptions.DECK_SPECIFIC ->
                            ReviewReminderScope.DeckSpecific(
                                viewModel.deckSelected.value ?: Consts.DEFAULT_DECK_ID,
                            )
                        null -> ReviewReminderScope.Global
                    },
                enabled =
                    when (val mode = dialogMode) {
                        is DialogMode.Add -> true
                        is DialogMode.Edit -> mode.reminderToBeEdited.enabled
                    },
            )

        Timber.d("Reminder to be returned: $reminderToBeReturned")
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
         * Request key for the time picker sub-dialog. Public so [TimePickerDialog] can also access it.
         */
        const val TIME_FRAGMENT_RESULT_REQUEST_KEY = "time_fragment_result_request_key"

        /**
         * The default time that will be set in the dialog when a new review reminder is being created.
         * Here, hour = 12 and minute = 0 indicates noon.
         */
        private val INITIAL_TIME = ReviewReminderTime(12, 0)

        /**
         * The default snooze radio that will be selected in the dialog when a new review reminder is being created.
         * We set it to DISABLED so as not to overwhelm the user with too many options when the add menu first opens.
         */
        private val INITIAL_SNOOZE_RADIO = AddEditReminderDialogViewModel.SnoozeRadioOptions.DISABLED

        /**
         * The default interval between snoozes that will be set in the dialog when a new review reminder is being created,
         * or when the user enables snoozing on an existing reminder that did not have snoozing previously enabled.
         * This is an Int because that is what the EditText's inputType is.
         * We set it to a reasonable 15 minutes.
         */
        private const val INITIAL_SNOOZE_INTERVAL_IN_MINUTES: Int = 15

        /**
         * The default maximum number of snoozes that will be set in the dialog when a new review reminder is being created,
         * or when the user enables a set amount of snoozes on an existing reminder that did not have set-amount snoozing previously enabled.
         * This is an Int because that is what the EditText's inputType is.
         * We set it to 3.
         */
        private const val INITIAL_MAX_SNOOZES: Int = 3

        /**
         * The default minimum card trigger threshold that is filled into the dialog when a new review
         * reminder is being created. Since this is set to one, the default behaviour is that users
         * will not get notified about a deck if there are no cards to review for that deck.
         * Users may choose to instead set it to zero, or any other non-negative integer value.
         * This is an Int because that is what the EditText's inputType is.
         */
        private const val INITIAL_CARD_THRESHOLD: Int = 1

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
