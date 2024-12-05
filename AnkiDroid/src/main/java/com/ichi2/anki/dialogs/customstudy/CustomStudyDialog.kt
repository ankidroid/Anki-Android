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
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import anki.scheduler.CustomStudyDefaultsResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_RANDOM
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_REV
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
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Consts.DynPriority
import com.ichi2.libanki.Deck
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.getNullableInt
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.cancelable
import com.ichi2.utils.customView
import com.ichi2.utils.listItems
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

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
@KotlinCleanup("remove 'collection' parameter and use withCol { }")
@KotlinCleanup("remove 'customStudyListener' parameter and use FragmentResult")
class CustomStudyDialog(
    private val collection: Collection,
    private val customStudyListener: CustomStudyListener?,
) : AnalyticsDialogFragment(),
    TagsDialogListener {
    interface CustomStudyListener {
        fun onCreateCustomStudySession()

        fun onExtendStudyLimits()
    }

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

    fun withArguments(
        did: DeckId,
        contextMenuAttribute: ContextMenuOption? = null,
    ): CustomStudyDialog {
        val args = this.arguments ?: Bundle()
        args.apply {
            if (contextMenuAttribute != null) {
                putInt(ARG_SUB_DIALOG_ID, contextMenuAttribute.ordinal)
            }
            putLong(ARG_DID, did)
        }
        this.arguments = args
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerFragmentResultReceiver()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val option = selectedSubDialog
        this.defaults = collection.sched.customStudyDefaults(dialogDeckId).toDomainModel()
        return if (option == null) {
            Timber.i("Showing Custom Study main menu")
            // Select the specified deck
            collection.decks.select(dialogDeckId)
            buildContextMenu()
        } else {
            Timber.i("Showing Custom Study dialog: $option")
            buildInputDialog(option)
        }
    }

    private fun buildContextMenu(): AlertDialog {
        val listIds = getListIds()

        return AlertDialog
            .Builder(requireActivity())
            .title(text = TR.actionsCustomStudy().toSentenceCase(this, R.string.sentence_custom_study))
            .cancelable(true)
            .listItems(items = listIds.map { it.getTitle(resources) }) { _, index ->
                when (listIds[index]) {
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
                                allTags = ArrayList(collection.tags.byDeck(dialogDeckId)),
                            )
                        requireActivity().showDialogFragment(dialogFragment)
                    }
                    STUDY_NEW,
                    STUDY_REV,
                    STUDY_FORGOT,
                    STUDY_AHEAD,
                    STUDY_RANDOM,
                    STUDY_PREVIEW,
                    -> {
                        // User asked for a standard custom study option
                        val d =
                            CustomStudyDialog(collection, customStudyListener)
                                .withArguments(dialogDeckId, listIds[index])
                        requireActivity().showDialogFragment(d)
                    }
                }
            }.create()
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param contextMenuOption the option of the dialog
     */
    private fun buildInputDialog(contextMenuOption: ContextMenuOption): AlertDialog {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
         */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        @SuppressLint("InflateParams")
        val v = requireActivity().layoutInflater.inflate(R.layout.styled_custom_study_details_dialog, null)
        val textView1 = v.findViewById<TextView>(R.id.custom_study_details_text1)
        val textView2 = v.findViewById<TextView>(R.id.custom_study_details_text2)
        val editText = v.findViewById<EditText>(R.id.custom_study_details_edittext2)
        // Set the text
        textView1.text = text1
        textView2.text = text2
        editText.setText(defaultValue)
        // Give EditText focus and show keyboard
        editText.setSelectAllOnFocus(true)
        editText.requestFocus()
        if (contextMenuOption == STUDY_NEW || contextMenuOption == STUDY_REV) {
            editText.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
        }
        // Set material dialog parameters
        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .customView(view = v, paddingLeft = 64, paddingRight = 64, paddingTop = 32, paddingBottom = 32)
                .positiveButton(R.string.dialog_ok) {
                    // Get the value selected by user
                    val n: Int =
                        try {
                            editText.text.toString().toInt()
                        } catch (e: Exception) {
                            Timber.w(e)
                            // This should never happen because we disable positive button for non-parsable inputs
                            return@positiveButton
                        }
                    when (contextMenuOption) {
                        STUDY_NEW -> {
                            requireActivity().sharedPrefs().edit { putInt("extendNew", n) }
                            val deck = collection.decks.get(dialogDeckId)!!
                            deck.put("extendNew", n)
                            collection.decks.save(deck)
                            collection.sched.extendLimits(n, 0)
                            onLimitsExtended()
                        }
                        STUDY_REV -> {
                            requireActivity().sharedPrefs().edit { putInt("extendRev", n) }
                            val deck = collection.decks.get(dialogDeckId)!!
                            deck.put("extendRev", n)
                            collection.decks.save(deck)
                            collection.sched.extendLimits(0, n)
                            onLimitsExtended()
                        }
                        STUDY_FORGOT -> {
                            val ar = JSONArray()
                            ar.put(0, 1)
                            createCustomStudySession(
                                ar,
                                arrayOf(
                                    String.format(
                                        Locale.US,
                                        "rated:%d:1",
                                        n,
                                    ),
                                    Consts.DYN_MAX_SIZE,
                                    Consts.DYN_RANDOM,
                                ),
                                false,
                            )
                        }
                        STUDY_AHEAD -> {
                            createCustomStudySession(
                                JSONArray(),
                                arrayOf(
                                    String.format(
                                        Locale.US,
                                        "prop:due<=%d",
                                        n,
                                    ),
                                    Consts.DYN_MAX_SIZE,
                                    Consts.DYN_DUE,
                                ),
                                true,
                            )
                        }
                        STUDY_RANDOM -> {
                            createCustomStudySession(JSONArray(), arrayOf("", n, Consts.DYN_RANDOM), true)
                        }
                        STUDY_PREVIEW -> {
                            createCustomStudySession(
                                JSONArray(),
                                arrayOf(
                                    "is:new added:$n",
                                    Consts.DYN_MAX_SIZE,
                                    Consts.DYN_OLDEST,
                                ),
                                false,
                            )
                        }
                        STUDY_TAGS -> TODO("This branch has not been covered before")
                    }
                }.negativeButton(R.string.dialog_cancel) {
                    requireActivity().dismissAllDialogFragments()
                }.create() // Added .create() because we wanted to access alertDialog positive button enable state
        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int,
                ) {}

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int,
                ) {}

                override fun afterTextChanged(editable: Editable) {
                    try {
                        editText.text.toString().toInt()
                        dialog.positiveButton.isEnabled = true
                    } catch (e: Exception) {
                        Timber.w(e)
                        dialog.positiveButton.isEnabled = false
                    }
                }
            },
        )

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
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
        createCustomStudySession(
            JSONArray(),
            arrayOf(
                sb.toString(),
                Consts.DYN_MAX_SIZE,
                Consts.DYN_RANDOM,
            ),
            true,
        )
    }

    /**
     * Retrieve the list of ids to put in the context menu list
     * @return the ids of which values to show
     */
    private fun getListIds(): List<ContextMenuOption> {
        // Standard context menu
        return mutableListOf(STUDY_FORGOT, STUDY_AHEAD, STUDY_RANDOM, STUDY_PREVIEW, STUDY_TAGS).apply {
            if (defaults.extendReview.isUsable) {
                this.add(0, STUDY_REV)
            }
            // We want 'Extend new' above 'Extend review' if both appear
            if (defaults.extendNew.isUsable) {
                this.add(0, STUDY_NEW)
            }
        }
    }

    /** Line 1 of the number entry dialog */
    private val text1: String
        get() =
            when (selectedSubDialog) {
                STUDY_NEW -> defaults.labelForNewQueueAvailable()
                STUDY_REV -> defaults.labelForReviewQueueAvailable()
                STUDY_FORGOT,
                STUDY_AHEAD,
                STUDY_RANDOM,
                STUDY_PREVIEW,
                STUDY_TAGS,
                null,
                -> ""
            }

    private val text2: String
        get() {
            val res = resources
            return when (selectedSubDialog) {
                STUDY_NEW -> res.getString(R.string.custom_study_new_extend)
                STUDY_REV -> res.getString(R.string.custom_study_rev_extend)
                STUDY_FORGOT -> res.getString(R.string.custom_study_forgotten)
                STUDY_AHEAD -> res.getString(R.string.custom_study_ahead)
                STUDY_RANDOM -> res.getString(R.string.custom_study_random)
                STUDY_PREVIEW -> res.getString(R.string.custom_study_preview)
                STUDY_TAGS,
                null,
                -> ""
            }
        }
    private val defaultValue: String
        get() {
            val prefs = requireActivity().sharedPrefs()
            return when (selectedSubDialog) {
                STUDY_NEW -> defaults.extendNew.initialValue.toString()
                STUDY_REV -> defaults.extendReview.initialValue.toString()
                STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
                STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
                STUDY_RANDOM -> prefs.getInt("randomCards", 100).toString()
                STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
                STUDY_TAGS,
                null,
                -> ""
            }
        }

    /**
     * Create a custom study session
     * @param delays delay options for scheduling algorithm
     * @param terms search terms
     * @param resched whether to reschedule the cards based on the answers given (or ignore them if false)
     */
    private fun createCustomStudySession(
        delays: JSONArray,
        terms: Array<Any>,
        resched: Boolean,
    ) {
        val dyn: Deck

        val decks = collection.decks
        val deckToStudyName = decks.name(dialogDeckId)
        val customStudyDeck = resources.getString(R.string.custom_study_deck_name)
        val cur = decks.byName(customStudyDeck)
        if (cur != null) {
            Timber.i("Found deck: '%s'", customStudyDeck)
            if (cur.isNormal) {
                Timber.w("Deck: '%s' was non-dynamic", customStudyDeck)
                showThemedToast(requireContext(), getString(R.string.custom_study_deck_exists), true)
                return
            } else {
                Timber.i("Emptying dynamic deck '%s' for custom study", customStudyDeck)
                // safe to empty
                collection.sched.emptyDyn(cur.getLong("id"))
                // reuse; don't delete as it may have children
                dyn = cur
                decks.select(cur.getLong("id"))
            }
        } else {
            Timber.i("Creating Dynamic Deck '%s' for custom study", customStudyDeck)
            dyn =
                try {
                    decks.get(decks.newFiltered(customStudyDeck))!!
                } catch (ex: BackendDeckIsFilteredException) {
                    showThemedToast(requireActivity(), ex.localizedMessage ?: ex.message ?: "", true)
                    return
                }
        }
        if (!dyn.has("terms")) {
            // #5959 - temp code to diagnose why terms doesn't exist.
            // normally we wouldn't want to log this much, but we need to know how deep the corruption is to fix the
            // issue
            Timber.w("Invalid Dynamic Deck: %s", dyn)
            CrashReportService.sendExceptionReport("Custom Study Deck had no terms", "CustomStudyDialog - createCustomStudySession")
            showThemedToast(requireContext(), getString(R.string.custom_study_rebuild_deck_corrupt), false)
            return
        }
        // and then set various options
        if (delays.length() > 0) {
            dyn.put("delays", delays)
        } else {
            dyn.put("delays", JSONObject.NULL)
        }
        val ar = dyn.getJSONArray("terms")
        ar.getJSONArray(0).put(0, """deck:"$deckToStudyName" terms[0]""")
        ar.getJSONArray(0).put(1, terms[1])
        @DynPriority val priority = terms[2] as Int
        ar.getJSONArray(0).put(2, priority)
        dyn.put("resched", resched)
        // Rebuild the filtered deck
        Timber.i("Rebuilding Custom Study Deck")
        // PERF: Should be in background
        collection.decks.save(dyn)
        // launch this in the activity scope, rather than the fragment scope
        requireActivity().launchCatchingTask { rebuildDynamicDeck() }
        // Hide the dialogs (required due to a DeckPicker issue)
        requireActivity().dismissAllDialogFragments()
    }

    private suspend fun rebuildDynamicDeck() {
        Timber.d("rebuildDynamicDeck()")
        withProgress {
            withCol { sched.rebuildDyn(decks.selected()) }
            customStudyListener?.onCreateCustomStudySession()
        }
    }

    private fun onLimitsExtended() {
        customStudyListener?.onExtendStudyLimits()
        requireActivity().dismissAllDialogFragments()
    }

    /**
     * Possible context menu options that could be shown in the custom study dialog.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    enum class ContextMenuOption(
        val getTitle: Resources.() -> String,
    ) {
        /** Increase today's new card limit */
        STUDY_NEW({ TR.customStudyIncreaseTodaysNewCardLimit() }),

        /** Increase today's review card limit */
        STUDY_REV({ TR.customStudyIncreaseTodaysReviewCardLimit() }),

        /** Review forgotten cards */
        STUDY_FORGOT({ TR.customStudyReviewForgottenCards() }),

        /** Review ahead */
        STUDY_AHEAD({ TR.customStudyReviewAhead() }),

        STUDY_RANDOM({ getString(R.string.custom_study_random_selection) }),

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
    private class CustomStudyDefaults(
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
