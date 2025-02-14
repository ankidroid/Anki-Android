/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

package com.ichi2.anki.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Insets
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets.Type.displayCutout
import android.view.WindowInsets.Type.navigationBars
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import anki.card_rendering.EmptyCardsReport
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchFailure
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchResult
import com.ichi2.anki.dialogs.EmptyCardsUiState.SearchingForEmptyCards
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.withProgress
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.emptyCids
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A dialog that searches for empty cards and presents the user with the option to delete them.
 * A user may 'keep notes', which retains the first card of each note, even if the note is empty.
 */
class EmptyCardsDialogFragment : DialogFragment() {
    private val viewModel by viewModels<EmptyCardsViewModel>()
    private val loadingContainer: View?
        get() = dialog?.findViewById(R.id.loading_container)
    private val loadingMessage: TextView?
        get() = dialog?.findViewById(R.id.text)
    private val emptyCardsResultsContainer: View?
        get() = dialog?.findViewById(R.id.empty_cards_results_container)
    private val emptyReportMessage: TextView?
        get() = dialog?.findViewById(R.id.empty_report_label)
    private val keepNotesWithNoValidCards: CheckBox?
        get() = dialog?.findViewById(R.id.preserve_notes)
    private val reportScrollView: ScrollView?
        get() = dialog?.findViewById(R.id.reportScrollView)
    private val reportView: TextView?
        get() = dialog?.findViewById(R.id.report)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        bindToState()
        viewModel.searchForEmptyCards()
        val dialogView = layoutInflater.inflate(R.layout.dialog_empty_cards, null)
        dialogView.findViewById<CheckBox>(R.id.preserve_notes)?.text =
            TR.emptyCardsPreserveNotesCheckbox()
        dialogView.findViewById<TextView>(R.id.report).movementMethod = LinkMovementMethod.getInstance()

        return AlertDialog
            .Builder(requireContext())
            .show {
                setTitle(
                    TR
                        .emptyCardsWindowTitle()
                        .toSentenceCase(context, R.string.sentence_empty_cards),
                )
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    val state = viewModel.uiState.value
                    if (state is EmptyCardsSearchResult) {
                        // this dialog is only shown from DeckPicker so we use it directly to avoid
                        // fragment result listeners and the possibility of the search report
                        // exceeding the result Bundle's limits
                        if (state.emptyCardsReport.emptyCids().isEmpty()) return@setPositiveButton
                        (requireActivity() as DeckPicker).startDeletingEmptyCards(
                            state.emptyCardsReport,
                            keepNotesWithNoValidCards?.isChecked ?: true,
                        )
                    }
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ ->
                    Timber.i("Empty cards dialog cancelled")
                }
                setView(dialogView)
            }.also {
                // the initial start state is a loading state as we are looking for the empty cards,
                // so there's no "action" for ok just yet
                it.positiveButton.isEnabled = false
            }
    }

    private fun bindToState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SearchingForEmptyCards -> {
                            loadingMessage?.text = getString(R.string.emtpy_cards_finding)
                            loadingContainer?.isVisible = true
                            emptyCardsResultsContainer?.isVisible = false
                            (dialog as? AlertDialog)?.positiveButton?.apply {
                                isEnabled = false
                                text = getString(R.string.dialog_ok)
                            }
                        }

                        is EmptyCardsSearchResult -> {
                            loadingContainer?.isVisible = false
                            val emptyCards = state.emptyCardsReport.emptyCids()
                            if (emptyCards.isEmpty()) {
                                emptyReportMessage?.text = TR.emptyCardsNotFound()
                                emptyReportMessage?.isVisible = true
                                // nothing to delete so also hide the preserve notes check box
                                keepNotesWithNoValidCards?.isVisible = false
                                (dialog as? AlertDialog)?.positiveButton?.text =
                                    getString(R.string.dialog_ok)
                            } else {
                                reportScrollView?.updateViewHeight()
                                reportView?.setText(
                                    state.emptyCardsReport.asActionableReport(),
                                    TextView.BufferType.SPANNABLE,
                                )
                                keepNotesWithNoValidCards?.isVisible = true
                                emptyReportMessage?.isVisible = false
                                (dialog as? AlertDialog)?.positiveButton?.text =
                                    getString(R.string.dialog_positive_delete)
                                emptyCardsResultsContainer?.isVisible = true
                            }
                            (dialog as? AlertDialog)?.positiveButton?.isEnabled = true
                        }

                        is EmptyCardsSearchFailure -> {
                            // the dialog is informational so there's nothing to do but show the
                            // error and exit
                            AlertDialog.Builder(requireActivity()).show {
                                title(R.string.vague_error)
                                message(text = state.throwable.toString())
                                positiveButton(R.string.dialog_ok) { }
                            }
                            dismissNow()
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces the anki format [anki:nid:#nid](ex: [anki:nid:234783924354]) from the report with
     * just the nid as a [ClickableSpan] which will trigger a [CardBrowser] search for that nid.
     */
    private fun EmptyCardsReport.asActionableReport(): SpannableStringBuilder {
        val spannableReport =
            SpannableStringBuilder(HtmlCompat.fromHtml(report, HtmlCompat.FROM_HTML_MODE_LEGACY))
        AnkiNidTag.parseFromReport(spannableReport).forEach { tag ->
            // make nid clickable
            spannableReport.setSpan(
                BrowserSearchByNidSpan(requireContext(), tag.nid),
                tag.matchedNid.range.first,
                tag.matchedNid.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            // remove suffix
            spannableReport.delete(tag.matchedSuffix)
            // remove prefix
            spannableReport.delete(tag.matchedPrefix)
        }
        return spannableReport
    }

    private fun SpannableStringBuilder.delete(group: MatchGroup) {
        delete(group.range.first, group.range.last + 1)
    }

    /**
     * Represents a Regex over `[anki:nid:1234]`.
     *
     * @param matchedPrefix `[anki:nid:
     * @param matchedNid `1234`
     * @param nid `1234`
     * @param matchedSuffix `]`
     */
    private class AnkiNidTag(
        val matchedPrefix: MatchGroup,
        val matchedNid: MatchGroup,
        val nid: NoteId,
        val matchedSuffix: MatchGroup,
    ) {
        companion object {
            // https://github.com/ankitects/anki/blob/de7a693465ca302e457a4767c7f213c76478f0ee/qt/aqt/emptycards.py#L56-L60
            @Suppress("RegExpRedundantEscape")
            private val ankiNidPattern = Regex("(\\[anki:nid:)(\\d+)(\\])")

            /**
             * @return a [Sequence] of [AnkiNidTag]. Note: the method uses [Regex.findAll] in the
             * implementation and returns its [Sequence] so each [AnkiNidTag] is one by one matched
             * and returned for calling code to use. This allows the backing [SpannableStringBuilder]
             * to resize itself after work done on each [AnkiNidTag](ex. deleting parts of it) so as
             * the following tags are "found" they will have the proper ranges in the backing
             * [SpannableStringBuilder]. Returning the full lists of [AnkiNidTag] and then working
             * on them is an error and will crash.
             *
             * @see EmptyCardsReport.asActionableReport
             * @see SpannableStringBuilder.delete
             */
            fun parseFromReport(report: SpannableStringBuilder): Sequence<AnkiNidTag> {
                return ankiNidPattern.findAll(report).mapNotNull { result ->
                    // for an entry like [anki:nid:1234] we should have 4 groups: the entire match(
                    // [anki:nid:1234]) and the three groups we defined: '[anki:nid:', '1234', ']'
                    if (result.groups.size != 4) return@mapNotNull null
                    val matchedPrefix = result.groups[1] ?: return@mapNotNull null
                    val matchedNid = result.groups[2] ?: return@mapNotNull null
                    val nid = matchedNid.value.toLongOrNull() ?: return@mapNotNull null
                    val matchedSuffix = result.groups[3] ?: return@mapNotNull null

                    AnkiNidTag(matchedPrefix, matchedNid, nid, matchedSuffix)
                }
            }
        }
    }

    /**
     * A specialized [ClickableSpan] that on click will open the [CardBrowser] and initiate a
     * search with the passed [nid].
     *
     * @see CardBrowser
     */
    private class BrowserSearchByNidSpan(
        val context: Context,
        val nid: Long,
    ) : ClickableSpan() {
        override fun onClick(widget: View) {
            val browserSearchIntent = Intent(context, CardBrowser::class.java)
            browserSearchIntent.putExtra("search_query", "nid:$nid")
            browserSearchIntent.putExtra("all_decks", true)
            context.startActivity(browserSearchIntent)
        }
    }

    /**
     * The [ScrollView] in this dialog's custom layout doesn't properly fit the allocated height so
     * this method manually updates the [ScrollView]'s height to a value that fits, depending on
     * orientation and screen size.
     */
    private fun View.updateViewHeight() {
        val currentOrientation = requireContext().resources.configuration.orientation
        val targetPercent = if (currentOrientation == ORIENTATION_LANDSCAPE) 0.25 else 0.5
        val screenHeight =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics =
                    (context as Activity)
                        .windowManager
                        .currentWindowMetrics
                val insets: Insets =
                    windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(
                        navigationBars() or displayCutout(),
                    )
                windowMetrics.bounds.height() - (insets.top + insets.bottom)
            } else {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                (context as Activity)
                    .windowManager
                    .defaultDisplay
                    .getMetrics(displayMetrics)
                displayMetrics.heightPixels
            }
        val calculatedHeight = (screenHeight * targetPercent).toInt()
        (layoutParams as ViewGroup.LayoutParams).height = calculatedHeight
        layoutParams = layoutParams
        requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reportScrollView?.updateViewHeight()
    }

    companion object {
        const val TAG = "EmptyCardsDialog"
    }
}

// TODO the fragment should just send a fragment result to the DeckPicker with the report and keep
//  notes flag(currently the report can exceed the transport Bundle capacity so we call it directly)
fun DeckPicker.startDeletingEmptyCards(
    report: EmptyCardsReport,
    keepNotes: Boolean,
) {
    Timber.i(
        "Starting to delete found %d empty cards, keepNotes: %b",
        report.emptyCids().size,
        keepNotes,
    )
    launchCatchingTask {
        withProgress(TR.emptyCardsDeleting()) {
            viewModel.deleteEmptyCards(report, keepNotes).join()
        }
    }
}
