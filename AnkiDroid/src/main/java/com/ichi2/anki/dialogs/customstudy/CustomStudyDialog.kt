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
import android.util.TypedValue
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.customStudyRequest
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_TAGS
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Consts.DynPriority
import com.ichi2.libanki.Deck
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.undoableOp
import com.ichi2.utils.BundleUtils.getNullableInt
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.bundleOfNotNull
import com.ichi2.utils.cancelable
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.setPaddingRelative
import com.ichi2.utils.textAsIntOrNull
import com.ichi2.utils.title
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.json.JSONObject
import timber.log.Timber

/**
 * Implements custom studying either by:
 * 1. modifying the limits of the current selected deck for when the user has reached the daily deck limits and wishes to study more
 * 2. creating a filtered "Custom study deck" where a user can study outside the typical schedule
 *
 *
 * ## UI
 * [CustomStudyDialog] represents either:
 * * A [main menu][buildContextMenu], offering [methods of custom study][ContextMenuOption]:
 * * An [input dialog][buildInputDialog] to input additional constraints for a [ContextMenuOption]
 *   * Example: changing the number of new cards
 *
 * #### Not Implemented
 * Anki Desktop contains the following items which are not yet implemented
 * * Study by card state or tags
 *   * New cards only
 *   * Due cards only
 *   * All review cards in random order
 *   * All cards in random order (don't reschedule)
 *
 * ## Nomenclature
 * Filtered decks were previously known as 'dynamic' decks, and before that: 'cram' decks
 *
 * ## Links
 * * [https://docs.ankiweb.net/filtered-decks.html#custom-study](https://docs.ankiweb.net/filtered-decks.html#custom-study)
 * * [com.ichi2.libanki.sched.Scheduler.customStudyDefaults]
 *     * [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [com.ichi2.libanki.sched.Scheduler.customStudy]
 *     * [sched.proto: CustomStudyRequest](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyRequest+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py](https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py)
 */
@KotlinCleanup("remove 'runBlocking' calls'")
class CustomStudyDialog :
    AnalyticsDialogFragment(),
    TagsDialogListener {
    /** ID of the [Deck] which this dialog was created for */
    private val dialogDeckId: DeckId
        get() = requireArguments().getLong(ARG_DID)

    /**
     * `null` initially when the main view is shown
     * otherwise, the [ContextMenuOption] representing the current sub-dialog
     */
    private val selectedSubDialog: ContextMenuOption?
        get() = requireArguments().getNullableInt(ARG_SUB_DIALOG_ID)?.let { ContextMenuOption.entries[it] }

    /** @see CustomStudyDefaults */
    private lateinit var defaults: CustomStudyDefaults

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerFragmentResultReceiver()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val option = selectedSubDialog
        this.defaults = runBlocking { withCol { sched.customStudyDefaults(dialogDeckId).toDomainModel() } }
        return if (option == null) {
            Timber.i("Showing Custom Study main menu")
            // Select the specified deck
            runBlocking { withCol { decks.select(dialogDeckId) } }
            buildContextMenu()
        } else {
            Timber.i("Showing Custom Study dialog: $option")
            buildInputDialog(option)
        }
    }

    /**
     * Handles selecting an item from the main menu of the dialog
     */
    private fun onMenuItemSelected(item: ContextMenuOption) =
        launchCatchingTask {
            when (item) {
                STUDY_TAGS -> {
            /*
             * This is a special Dialog for CUSTOM STUDY, where instead of only collecting a
             * number, it is necessary to collect a list of tags. This case handles the creation
             * of that Dialog.
             */
                    val dialogFragment =
                        TagsDialog().withArguments(
                            context = requireContext(),
                            type = TagsDialog.DialogType.CUSTOM_STUDY_TAGS,
                            checkedTags = ArrayList(),
                            allTags = ArrayList(withCol { tags.byDeck(dialogDeckId) }),
                        )
                    requireActivity().showDialogFragment(dialogFragment)
                }
                EXTEND_NEW,
                EXTEND_REV,
                STUDY_FORGOT,
                STUDY_AHEAD,
                STUDY_PREVIEW,
                -> {
                    // User asked for a standard custom study option
                    val dialog: CustomStudyDialog = createInstance(dialogDeckId, item)
                    requireActivity().showDialogFragment(dialog)
                }
            }
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
        ContextMenuOption.entries
            .map {
                when (it) {
                    EXTEND_NEW -> Pair(it, defaults.extendNew.isUsable)
                    EXTEND_REV -> Pair(it, defaults.extendReview.isUsable)
                    else -> Pair(it, true)
                }
            }.forEach { (menuItem, isItemEnabled) ->
                (layoutInflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView)
                    .apply {
                        text = menuItem.getTitle(requireContext().resources)
                        isEnabled = isItemEnabled
                        setBackgroundResource(ta.resourceId)
                        setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                        setOnClickListener { onMenuItemSelected(menuItem) }
                    }.also { container.addView(it) }
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

        // Set material dialog parameters
        @Suppress("RedundantValueArgument") // click = null
        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .customView(view = v, paddingStart = 64, paddingEnd = 64, paddingTop = 32, paddingBottom = 32)
                .positiveButton(R.string.dialog_ok, click = null)
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

                requireActivity().launchCatchingTask {
                    try {
                        customStudy(contextMenuOption, n)
                    } finally {
                        requireActivity().dismissAllDialogFragments()
                    }
                }
            }
        }

        editText.doAfterTextChanged {
            dialog.positiveButton.isEnabled = editText.textAsIntOrNull() != null
        }

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private suspend fun customStudy(
        contextMenuOption: ContextMenuOption,
        userEntry: Int,
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
                    STUDY_TAGS -> TODO("This branch has not been covered before")
                }
            }

        undoableOp { sched.customStudy(request) }
        val action =
            when (contextMenuOption) {
                EXTEND_NEW, EXTEND_REV -> CustomStudyAction.EXTEND_STUDY_LIMITS
                STUDY_FORGOT, STUDY_AHEAD, STUDY_PREVIEW -> CustomStudyAction.CUSTOM_STUDY_SESSION
                STUDY_TAGS -> TODO("This branch has not been covered before")
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
            STUDY_TAGS -> TODO("This branch has not been covered before")
        }
    }

    /**
     * Gathers the final selection of tags and type of cards,
     * Generates the search screen for the custom study deck.
     */
    @NeedsTest("14537: limit to particular tags")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        val sb = StringBuilder(stateFilter.toSearch)
        val arr: MutableList<String?> = ArrayList(selectedTags.size)
        if (selectedTags.isNotEmpty()) {
            for (tag in selectedTags) {
                arr.add("tag:\"$tag\"")
            }
            sb.append("(").append(arr.joinToString(" or ")).append(")")
        }
        activity?.launchCatchingTask {
            withProgress {
                createTagsCustomStudySession(
                    arrayOf(
                        sb.toString(),
                        Consts.DYN_MAX_SIZE,
                        Consts.DYN_RANDOM,
                    ),
                )
            }
        }
    }

    /** Line 1 of the number entry dialog */
    private val text1: String
        get() =
            when (selectedSubDialog) {
                EXTEND_NEW -> defaults.labelForNewQueueAvailable()
                EXTEND_REV -> defaults.labelForReviewQueueAvailable()
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
                STUDY_TAGS,
                null,
                -> ""
            }
        }

    /** Initial value of the number entry dialog */
    private val defaultValue: String
        get() {
            val prefs = requireActivity().sharedPrefs()
            return when (selectedSubDialog) {
                EXTEND_NEW -> defaults.extendNew.initialValue.toString()
                EXTEND_REV -> defaults.extendReview.initialValue.toString()
                STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
                STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
                STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
                STUDY_TAGS,
                null,
                -> ""
            }
        }

    /**
     * Create a custom study session
     * @param terms search terms
     */
    private suspend fun createTagsCustomStudySession(terms: Array<Any>) {
        val dyn: Deck

        val deckToStudyName = withCol { decks.name(dialogDeckId) }
        val customStudyDeck = resources.getString(R.string.custom_study_deck_name)
        val cur = withCol { decks.byName(customStudyDeck) }
        if (cur != null) {
            Timber.i("Found deck: '%s'", customStudyDeck)
            if (cur.isNormal) {
                Timber.w("Deck: '%s' was non-dynamic", customStudyDeck)
                showThemedToast(requireContext(), getString(R.string.custom_study_deck_exists), true)
                return
            } else {
                Timber.i("Emptying dynamic deck '%s' for custom study", customStudyDeck)
                // safe to empty
                withCol { sched.emptyDyn(cur.getLong("id")) }
                // reuse; don't delete as it may have children
                dyn = cur
                withCol { decks.select(cur.getLong("id")) }
            }
        } else {
            Timber.i("Creating Dynamic Deck '%s' for custom study", customStudyDeck)
            dyn =
                try {
                    withCol { decks.get(decks.newFiltered(customStudyDeck))!! }
                } catch (ex: BackendDeckIsFilteredException) {
                    showThemedToast(requireActivity(), ex.localizedMessage ?: ex.message ?: "", true)
                    return
                }
        }
        // and then set various options
        dyn.put("delays", JSONObject.NULL)
        val ar = dyn.getJSONArray("terms")
        ar.getJSONArray(0).put(0, """deck:"$deckToStudyName" ${terms[0]}""")
        ar.getJSONArray(0).put(1, terms[1])
        @DynPriority val priority = terms[2] as Int
        ar.getJSONArray(0).put(2, priority)
        dyn.put("resched", true)
        // Rebuild the filtered deck
        Timber.i("Rebuilding Custom Study Deck")
        // PERF: Should be in background
        withCol { decks.save(dyn) }
        Timber.d("Rebuilding dynamic deck...")
        withCol { sched.rebuildDyn(decks.selected()) }
        setFragmentResult(
            CustomStudyAction.REQUEST_KEY,
            bundleOf(
                CustomStudyAction.BUNDLE_KEY to CustomStudyAction.CUSTOM_STUDY_SESSION.ordinal,
            ),
        )
        // Hide the dialogs (required due to a DeckPicker issue)
        requireActivity().dismissAllDialogFragments()
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
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    enum class ContextMenuOption(
        val getTitle: Resources.() -> String,
    ) {
        /** Increase today's new card limit */
        EXTEND_NEW({ TR.customStudyIncreaseTodaysNewCardLimit() }),

        /** Increase today's review card limit */
        EXTEND_REV({ TR.customStudyIncreaseTodaysReviewCardLimit() }),

        /** Review forgotten cards */
        STUDY_FORGOT({ TR.customStudyReviewForgottenCards() }),

        /** Review ahead */
        STUDY_AHEAD({ TR.customStudyReviewAhead() }),

        /** Preview new cards */
        STUDY_PREVIEW({ TR.customStudyPreviewNewCards() }),

        /** Limit to particular tags */
        STUDY_TAGS({ getString(R.string.custom_study_limit_tags) }),
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
             * "Extend" only has an effect if there are pending cards in the target deck
             *
             * The number of pending cards in child decks is only informative
             */
            val isUsable
                get() = available > 0

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
        fun createInstance(
            deckId: DeckId,
            contextMenuAttribute: ContextMenuOption? = null,
        ): CustomStudyDialog =
            CustomStudyDialog().apply {
                arguments =
                    bundleOfNotNull(
                        ARG_DID to deckId,
                        contextMenuAttribute?.let { ARG_SUB_DIALOG_ID to it.ordinal },
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
