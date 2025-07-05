/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs.customstudy

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Resources
import android.os.Bundle
import android.os.Parcelable
import android.util.TypedValue
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.copy
import anki.scheduler.customStudyRequest
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.asyncIO
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_TAGS
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.TagLimitFragment.Companion.KEY_EXCLUDED_TAGS
import com.ichi2.anki.dialogs.customstudy.TagLimitFragment.Companion.KEY_INCLUDED_TAGS
import com.ichi2.anki.dialogs.customstudy.TagLimitFragment.Companion.REQUEST_CUSTOM_STUDY_TAGS
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.utils.BundleUtils.getNullableInt
import com.ichi2.utils.bundleOfNotNull
import com.ichi2.utils.cancelable
import com.ichi2.utils.coMeasureTime
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.setPaddingRelative
import com.ichi2.utils.textAsIntOrNull
import com.ichi2.utils.title
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * Implements custom studying either by:
 * 1. modifying the limits of the current selected deck for when the user has reached the daily
 *    deck limits and wishes to study more
 * 2. creating a filtered "Custom study deck" where a user can study outside the typical schedule
 *
 *
 * ## UI
 * [CustomStudyDialog] represents either:
 * * A [main menu][buildContextMenu], offering [methods of custom study][ContextMenuOption]
 * * An [input dialog][buildInputDialog] to input additional constraints for a [ContextMenuOption]
 *    * Example: changing the number of new cards
 *
 * Note: when studying by tags the input dialog will also display a state selector and on user
 * action will show one extra dialog(for actual tag selection, see [TagLimitFragment])
 *
 * ## Nomenclature
 * Filtered decks were previously known as 'dynamic' decks, and before that: 'cram' decks
 *
 * ## Links
 * * [https://docs.ankiweb.net/filtered-decks.html#custom-study](https://docs.ankiweb.net/filtered-decks.html#custom-study)
 * * [com.ichi2.anki.libanki.sched.Scheduler.customStudyDefaults]
 *     * [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [com.ichi2.anki.libanki.sched.Scheduler.customStudy]
 *     * [sched.proto: CustomStudyRequest](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyRequest+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py](https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py)
 * * [https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit](https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit.py)
 *
 * @see TagLimitFragment
 */
@KotlinCleanup("remove 'runBlocking' call'")
@NeedsTest("deferredDefaults")
class CustomStudyDialog : AnalyticsDialogFragment() {
    /** ID of the [Deck] which this dialog was created for */
    private val dialogDeckId: DeckId
        get() = requireArguments().getLong(ARG_DID)

    /**
     * `null` initially when the main view is shown
     * otherwise, the [ContextMenuOption] representing the current sub-dialog
     */
    private val selectedSubDialog: ContextMenuOption?
        get() = requireArguments().getNullableInt(ARG_SUB_DIALOG_ID)?.let { ContextMenuOption.entries[it] }

    private val selectedStatePosition: Int
        get() =
            dialog
                ?.findViewById<Spinner>(R.id.cards_state_selector)
                ?.selectedItemPosition ?: AdapterView.INVALID_POSITION
    private val userInputValue: Int?
        get() =
            dialog
                ?.findViewById<EditText>(R.id.custom_study_details_edittext2)
                ?.textAsIntOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(REQUEST_CUSTOM_STUDY_TAGS, this) { _, bundle ->
            val tagsToInclude = bundle.getStringArrayList(KEY_INCLUDED_TAGS) ?: emptyList<String>()
            val tagsToExclude = bundle.getStringArrayList(KEY_EXCLUDED_TAGS) ?: emptyList<String>()
            val option = selectedSubDialog ?: return@setFragmentResultListener
            if (selectedStatePosition == AdapterView.INVALID_POSITION) return@setFragmentResultListener
            val kind = CustomStudyCardState.entries[selectedStatePosition].kind
            val cardsAmount = userInputValue ?: 100 // the default value
            launchCustomStudy(option, cardsAmount, kind, tagsToInclude, tagsToExclude)
        }
    }

    /** @see customStudy */
    private fun launchCustomStudy(
        option: ContextMenuOption,
        cardsAmount: Int,
        kind: CramKind = CramKind.CRAM_KIND_NEW,
        tagsToInclude: List<String> = emptyList(),
        tagsToExclude: List<String> = emptyList(),
    ) {
        requireActivity().launchCatchingTask(
            // net.ankiweb.rsdroid.BackendException: No cards matched the criteria you provided.
            // TODO (Backend#256: make this BackendCustomStudyException)
            skipCrashReport = { it is BackendException },
        ) {
            withProgress {
                try {
                    customStudy(option, cardsAmount, kind, tagsToInclude, tagsToExclude)
                } finally {
                    requireActivity().dismissAllDialogFragments()
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val option = selectedSubDialog
        return if (option == null) {
            Timber.i("Showing Custom Study main menu")
            deferredDefaults = loadCustomStudyDefaults()
            // Select the specified deck
            runBlocking { withCol { decks.select(dialogDeckId) } }
            buildContextMenu()
        } else {
            Timber.i("Showing Custom Study dialog: $option")
            buildInputDialog(option)
        }
    }

    /**
     * Continues the custom study process by showing an input dialog where the user can enter an
     * amount specific to that type of custom study(eg. cards, days etc).
     */
    private suspend fun onMenuItemSelected(item: ContextMenuOption) {
        // on a slow phone, 'extend limits' may be clicked before we know there's no new/review cards
        // show 'no cards due' if this occurs
        if (item.checkAvailability != null) {
            val defaults = withProgress { deferredDefaults.await() }
            if (!item.checkAvailability(defaults)) {
                showSnackbar(getString((R.string.studyoptions_no_cards_due)))
                return
            }
        }

        val dialog: CustomStudyDialog = createSubDialog(dialogDeckId, item)
        requireActivity().showDialogFragment(dialog)
    }

    private fun buildContextMenu(): AlertDialog {
        val customMenuView = ScrollView(requireContext())
        val container =
            LinearLayout(requireContext())
                .apply {
                    orientation = LinearLayout.VERTICAL
                    setPaddingRelative(9.dp.toPx(requireContext()))
                }
        customMenuView.addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        val ta = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, ta, true)

        fun buildMenuItems() {
            ContextMenuOption.entries
                .map { option ->
                    Pair(
                        option,
                        // if there's no availability check, it's enabled
                        option.checkAvailability == null ||
                            // if data hasn't loaded, defer the check and assume it's enabled
                            !deferredDefaults.isCompleted ||
                            // if unavailable, disable the item
                            option.checkAvailability(deferredDefaults.getCompleted()),
                    )
                }.forEach { (menuItem, isItemEnabled) ->
                    (layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView)
                        .apply {
                            text = menuItem.getTitle(requireContext().resources)
                            isEnabled = isItemEnabled
                            setBackgroundResource(ta.resourceId)
                            setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                            setOnClickListener {
                                launchCatchingTask { onMenuItemSelected(menuItem) }
                            }
                        }.also { container.addView(it) }
                }
        }

        buildMenuItems()

        // add a continuation if 'defaults' was not loaded
        if (!deferredDefaults.isCompleted) {
            launchCatchingTask {
                Timber.d("awaiting 'defaults' continuation")
                deferredDefaults.await()
                container.removeAllViews()
                buildMenuItems()
            }
        }

        return AlertDialog
            .Builder(requireActivity())
            .title(text = TR.actionsCustomStudy().toSentenceCase(this, R.string.sentence_custom_study))
            .cancelable(true)
            .customView(customMenuView)
            .create()
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param contextMenuOption the option of the dialog
     */
    @NeedsTest("17757: fragment not dismissed before result is output")
    private fun buildInputDialog(contextMenuOption: ContextMenuOption): AlertDialog {
        require(deferredDefaults.isCompleted || selectedSubDialog!!.checkAvailability == null)
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
         */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        @SuppressLint("InflateParams")
        val v = requireActivity().layoutInflater.inflate(R.layout.styled_custom_study_details_dialog, null)
        v.findViewById<TextView>(R.id.custom_study_details_text1).apply {
            text = text1
        }
        v.findViewById<TextView>(R.id.custom_study_details_text2).apply {
            text = text2
        }
        v.findViewById<Spinner>(R.id.cards_state_selector).apply {
            isVisible = contextMenuOption == STUDY_TAGS
            adapter =
                ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    CustomStudyCardState.entries.map { it.labelProducer() },
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
        }
        val editText =
            v.findViewById<EditText>(R.id.custom_study_details_edittext2).apply {
                setText(defaultValue)
                // Give EditText focus and show keyboard
                setSelectAllOnFocus(true)
                requestFocus()
                // a user may enter a negative value when extending limits
                if (contextMenuOption == EXTEND_NEW || contextMenuOption == EXTEND_REV) {
                    inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
                }
            }
        val positiveBtnLabel =
            if (contextMenuOption == STUDY_TAGS) {
                TR.customStudyChooseTags().toSentenceCase(requireContext(), R.string.sentence_choose_tags)
            } else {
                getString(R.string.dialog_ok)
            }

        // Set material dialog parameters
        @Suppress("RedundantValueArgument") // click = null
        val horizontalPadding = 32.dp.toPx(requireContext())
        val verticalPadding = 16.dp.toPx(requireContext())
        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .customView(
                    view = v,
                    paddingStart = horizontalPadding,
                    paddingEnd = horizontalPadding,
                    paddingTop = verticalPadding,
                    paddingBottom = verticalPadding,
                ).positiveButton(text = positiveBtnLabel, click = null)
                .negativeButton(R.string.dialog_cancel) {
                    requireActivity().dismissAllDialogFragments()
                }.create()

        var allowSubmit = true
        // we set the listener here so 'ok' doesn't immediately close the dialog.
        // if it did, we would not have had time to execute the method, and would not be
        // able to output a fragment result
        dialog.setOnShowListener {
            dialog.positiveButton.setOnClickListener {
                // prevent race conditions
                if (!allowSubmit) return@setOnClickListener
                allowSubmit = false

                // Get the value selected by user
                val n =
                    editText.textAsIntOrNull() ?: run {
                        Timber.w("Non-numeric user input was provided")
                        Timber.d("value: %s", editText.text.toString())
                        allowSubmit = true
                        return@setOnClickListener
                    }
                if (contextMenuOption == STUDY_TAGS) {
                    // mark allowSubmit as true because, if the user cancels TagLimitFragment, when
                    // we come back we wouldn't be able to trigger again TagLimitFragment
                    allowSubmit = true
                    val tagsSelectionDialog = TagLimitFragment.newInstance(dialogDeckId)
                    tagsSelectionDialog.show(parentFragmentManager, TagLimitFragment.TAG)
                    return@setOnClickListener
                }
                launchCustomStudy(contextMenuOption, n)
            }
        }

        editText.doAfterTextChanged {
            dialog.positiveButton.isEnabled = editText.textAsIntOrNull() != null
        }

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    // TODO cram kind and the included/excluded tags lists are only relevant for STUDY_TAGS and
    //  should be included in the option to not leak in the method's api
    private suspend fun customStudy(
        contextMenuOption: ContextMenuOption,
        userEntry: Int,
        cramKind: CramKind,
        tagsSelectedForInclude: List<String>,
        tagsSelectedForExclude: List<String>,
    ) {
        Timber.i("Custom study: $contextMenuOption; input = $userEntry")

        val request =
            customStudyRequest {
                deckId = dialogDeckId
                when (contextMenuOption) {
                    EXTEND_NEW -> newLimitDelta = userEntry
                    EXTEND_REV -> reviewLimitDelta = userEntry
                    STUDY_FORGOT -> forgotDays = userEntry
                    STUDY_AHEAD -> reviewAheadDays = userEntry
                    STUDY_PREVIEW -> previewDays = userEntry
                    STUDY_TAGS -> {
                        // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/customstudy.py#L169-L177
                        cram =
                            cram.copy {
                                kind = cramKind
                                cardLimit = userEntry
                                tagsToInclude.addAll(tagsSelectedForInclude)
                                tagsToExclude.addAll(tagsSelectedForExclude)
                            }
                    }
                }
            }

        undoableOp { sched.customStudy(request) }
        val action =
            when (contextMenuOption) {
                EXTEND_NEW, EXTEND_REV -> CustomStudyAction.EXTEND_STUDY_LIMITS
                STUDY_FORGOT, STUDY_AHEAD, STUDY_PREVIEW, STUDY_TAGS -> CustomStudyAction.CUSTOM_STUDY_SESSION
            }

        setFragmentResult(CustomStudyAction.REQUEST_KEY, bundleOf(CustomStudyAction.BUNDLE_KEY to action.ordinal))

        // save the default values (not in upstream)
        when (contextMenuOption) {
            STUDY_FORGOT -> sharedPrefs().edit { putInt("forgottenDays", userEntry) }
            STUDY_AHEAD -> sharedPrefs().edit { putInt("aheadDays", userEntry) }
            STUDY_PREVIEW -> sharedPrefs().edit { putInt("previewDays", userEntry) }
            EXTEND_NEW, EXTEND_REV -> {
                // Nothing to do in ankidroid. The default value is provided by the backend.
            }
            STUDY_TAGS -> sharedPrefs().edit { putInt("amountOfCards", userEntry) }
        }
    }

    /**
     * Loads [CustomStudyDefaults] from the backend
     *
     * This method may be slow (> 1s)
     */
    private fun loadCustomStudyDefaults() =
        lifecycleScope.asyncIO {
            coMeasureTime("loadCustomStudyDefaults") {
                withCol { sched.customStudyDefaults(dialogDeckId).toDomainModel() }
            }
        }

    /**
     * Line 1 of the number entry dialog
     *
     * e.g. "Review forgotten cards"
     *
     * Requires [ContextMenuOption.checkAvailability] to be null/return true
     */
    private val text1: String
        get() =
            when (selectedSubDialog) {
                EXTEND_NEW -> deferredDefaults.getCompleted().labelForNewQueueAvailable()
                EXTEND_REV -> deferredDefaults.getCompleted().labelForReviewQueueAvailable()
                STUDY_FORGOT,
                STUDY_AHEAD,
                STUDY_PREVIEW,
                STUDY_TAGS,
                null,
                -> ""
            }

    /** Line 2 of the number entry dialog */
    private val text2: String
        get() {
            val res = resources
            return when (selectedSubDialog) {
                EXTEND_NEW -> res.getString(R.string.custom_study_new_extend)
                EXTEND_REV -> res.getString(R.string.custom_study_rev_extend)
                STUDY_FORGOT -> res.getString(R.string.custom_study_forgotten)
                STUDY_AHEAD -> res.getString(R.string.custom_study_ahead)
                STUDY_PREVIEW -> res.getString(R.string.custom_study_preview)
                STUDY_TAGS -> res.getString(R.string.custom_study_tags)
                null -> ""
            }
        }

    /**
     * Initial value of the number entry dialog
     *
     * Requires [ContextMenuOption.checkAvailability] to be null/return true
     */
    private val defaultValue: String
        get() {
            val prefs = requireActivity().sharedPrefs()
            return when (selectedSubDialog) {
                EXTEND_NEW ->
                    deferredDefaults
                        .getCompleted()
                        .extendNew.initialValue
                        .toString()
                EXTEND_REV ->
                    deferredDefaults
                        .getCompleted()
                        .extendReview.initialValue
                        .toString()
                STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
                STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
                STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
                // currently(as of Anki 25.02) not upstream
                STUDY_TAGS -> prefs.getInt("amountOfCards", 100).toString()
                null,
                -> ""
            }
        }

    /**
     * Represents actions for managing custom study sessions and extending study limits.
     * These actions are passed between fragments and activities via the FragmentResult API.
     */
    enum class CustomStudyAction {
        EXTEND_STUDY_LIMITS,
        CUSTOM_STUDY_SESSION,
        ;

        companion object {
            const val REQUEST_KEY = "CustomStudyDialog"
            const val BUNDLE_KEY = "action"

            /** Extracts a [CustomStudyAction] from a [Bundle] */
            fun fromBundle(bundle: Bundle): CustomStudyAction =
                bundle.getInt(CustomStudyAction.BUNDLE_KEY).let { actionOrdinal ->
                    entries.first { it.ordinal == actionOrdinal }
                }
        }
    }

    /**
     * Context menu options shown in the custom study dialog.
     *
     * @param checkAvailability Whether the menu option is available
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    enum class ContextMenuOption(
        val getTitle: Resources.() -> String,
        val checkAvailability: ((CustomStudyDefaults) -> Boolean)? = null,
    ) {
        /** Increase today's new card limit */
        EXTEND_NEW({ TR.customStudyIncreaseTodaysNewCardLimit() }, checkAvailability = { it.extendNew.isUsable }),

        /** Increase today's review card limit */
        EXTEND_REV({ TR.customStudyIncreaseTodaysReviewCardLimit() }, checkAvailability = { it.extendReview.isUsable }),

        /** Review forgotten cards */
        STUDY_FORGOT({ TR.customStudyReviewForgottenCards() }),

        /** Review ahead */
        STUDY_AHEAD({ TR.customStudyReviewAhead() }),

        /** Preview new cards */
        STUDY_PREVIEW({ TR.customStudyPreviewNewCards() }),

        /** Limit to particular tags */
        STUDY_TAGS({ TR.customStudyStudyByCardStateOrTag() }),
    }

    @Parcelize
    enum class CustomStudyCardState(
        val labelProducer: () -> String,
        val kind: CramKind,
    ) : Parcelable {
        NewCardsOnly({ TR.customStudyNewCardsOnly() }, CramKind.CRAM_KIND_NEW),
        DueCardsOnly({ TR.customStudyDueCardsOnly() }, CramKind.CRAM_KIND_DUE),
        ReviewCardsRandom({ TR.customStudyAllReviewCardsInRandomOrder() }, CramKind.CRAM_KIND_REVIEW),
        AllCardsRandom({ TR.customStudyAllCardsInRandomOrderDont() }, CramKind.CRAM_KIND_ALL),
    }

    /**
     * Default values for extending deck limits, and default tag selection
     *
     * Adapter which documents [anki.scheduler.CustomStudyDefaultsResponse]
     *
     * Upstream: [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
     */
    @VisibleForTesting
    class CustomStudyDefaults(
        val extendNew: ExtendLimits,
        val extendReview: ExtendLimits,
        @Suppress("unused")
        val tags: List<CustomStudyDefaultsResponse.Tag>,
    ) {
        /** Available new cards: 1 (2 in subdecks) */
        fun labelForNewQueueAvailable(): String = TR.customStudyAvailableNewCards2(extendNew.labelForCountWithChildren())

        /** Available review cards: 1 (2 in subdecks) */
        fun labelForReviewQueueAvailable(): String = TR.customStudyAvailableReviewCards2(extendReview.labelForCountWithChildren())

        /**
         * Data displayed to a user wanting to temporarily extend the daily limits of
         * either new/review cards for a deck
         *
         * Displays `Available new cards: 1 (2 in subdecks)` when a limit is reached with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        class ExtendLimits(
            /** The initial value to display in the input dialog */
            val initialValue: Int,
            /**
             * The number of pending cards in only the parent deck
             *
             * **Example**
             * Returns **1** when a limit is reached with remaining cards:
             * ```
             * Deck (1)
             *   Deck::Child1 (1)
             *   Deck::Child2 (1)
             * ```
             */
            val available: Int,
            /**
             * The sum of cards in only the child decks
             *
             * **Example**
             * * Returns **2** when a limit is reached  with remaining cards:
             * ```
             * Deck (1)
             *   Deck::Child1 (1)
             *   Deck::Child2 (1)
             * ```
             */
            val availableInChildren: Int,
        ) {
            /**
             * **Temporarily Disabled** - logic may be incorrect
             *
             * "Extend" only has an effect if there are pending cards in the target deck
             *
             * The number of pending cards in child decks is only informative
             */
            val isUsable
                get() = true // TODO: Confirm `available > 0` is correct; user feedback states subdecks are taken into account

            /**
             * A string representing the count of cards which have exceeded a deck limit
             *
             * `123 (456 in subdecks)` or `123`
             *
             * For use in either
             * [net.ankiweb.rsdroid.Translations.customStudyAvailableReviewCards2] or
             * [net.ankiweb.rsdroid.Translations.customStudyAvailableNewCards2]
             *
             */
            fun labelForCountWithChildren(): String =
                if (availableInChildren == 0) {
                    available.toString()
                } else {
                    "$available ${TR.customStudyAvailableChildCount(availableInChildren)}"
                }
        }

        companion object {
            fun CustomStudyDefaultsResponse.toDomainModel(): CustomStudyDefaults =
                CustomStudyDefaults(
                    extendNew =
                        ExtendLimits(
                            initialValue = extendNew,
                            available = availableNew,
                            availableInChildren = availableNewInChildren,
                        ),
                    extendReview =
                        ExtendLimits(
                            initialValue = extendReview,
                            available = availableReview,
                            availableInChildren = availableReviewInChildren,
                        ),
                    tags = this.tagsList,
                )
        }
    }

    companion object {
        /**
         * @see CustomStudyDefaults
         *
         * Singleton; initialized when the main screen is loaded
         * This exists so we don't need to pass an unbounded object between fragments
         */
        private lateinit var deferredDefaults: Deferred<CustomStudyDefaults>

        /**
         * Creates an instance of the Custom Study Dialog: a user can select a custom study type
         */
        fun createInstance(deckId: DeckId): CustomStudyDialog =
            CustomStudyDialog().apply {
                arguments =
                    bundleOfNotNull(
                        ARG_DID to deckId,
                    )
            }

        /**
         * Creates an instance of the Custom Study sub-dialog for a user to configure
         * a selected custom study type
         *
         * e.g. After selecting "Study Ahead", entering the number of days to study ahead by
         */
        fun createSubDialog(
            deckId: DeckId,
            contextMenuAttribute: ContextMenuOption,
        ): CustomStudyDialog =
            CustomStudyDialog().apply {
                arguments =
                    bundleOfNotNull(
                        ARG_DID to deckId,
                        ARG_SUB_DIALOG_ID to contextMenuAttribute.ordinal,
                    )
            }

        /**
         * (required) Key for the [DeckId] this dialog deals with.
         * @see CustomStudyDialog.dialogDeckId
         */
        private const val ARG_DID = "did"

        /**
         * (optional) Key for the ordinal of the [ContextMenuOption] to display.
         * @see CustomStudyDialog.selectedSubDialog
         */
        private const val ARG_SUB_DIALOG_ID = "subDialogId"
    }
}
