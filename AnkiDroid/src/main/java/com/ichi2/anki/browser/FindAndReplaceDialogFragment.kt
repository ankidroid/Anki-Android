/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_FIELD
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_MATCH_CASE
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_ONLY_SELECTED_NOTES
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_REGEX
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_REPLACEMENT
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.ARG_SEARCH
import com.ichi2.anki.browser.FindAndReplaceDialogFragment.Companion.REQUEST_FIND_AND_REPLACE
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog that shows the options for finding and replacing the text of notes in [CardBrowser].
 *
 * Note for completeness:
 *
 * Desktop also shows the fields of a note in the browser and the user can right-click on one of
 * them to start a find and replace only for that field. We display the fields only in
 * [ManageNotetypes] which doesn't feel like it should have this feature.
 * (see https://github.com/ankitects/anki/blob/64ca90934bc26ddf7125913abc9dd9de8cb30c2b/qt/aqt/browser/sidebar/tree.py#L1074)
 */
// TODO desktop offers history for inputs
class FindAndReplaceDialogFragment : AnalyticsDialogFragment() {
    private val browserViewModel by activityViewModels<CardBrowserViewModel>()
    private val fieldSelector: Spinner?
        get() = dialog?.findViewById(R.id.fields_selector)
    private val onlySelectedNotes: CheckBox?
        get() = dialog?.findViewById(R.id.check_only_selected_notes)
    private val contentViewsGroup: Group?
        get() = dialog?.findViewById(R.id.content_views_group)
    private val loadingViewsGroup: Group?
        get() = dialog?.findViewById(R.id.loading_views_group)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val contentView = layoutInflater.inflate(R.layout.fragment_find_replace, null)
        contentView.setupLabels()
        val title =
            TR
                .browsingFindAndReplace()
                .toSentenceCase(requireContext(), R.string.sentence_find_and_replace)
        return AlertDialog
            .Builder(requireContext())
            .show {
                title(text = title)
                customView(contentView)
                neutralButton(R.string.help) { openUrl(R.string.link_manual_browser_find_replace) }
                negativeButton(R.string.dialog_cancel)
                positiveButton(R.string.dialog_ok) { startFindReplace() }
            }.also { dialog ->
                dialog.positiveButton.isEnabled = false
            }
    }

    private fun View.setupLabels() {
        findViewById<TextView>(R.id.label_find).text =
            HtmlCompat.fromHtml(TR.browsingFind(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.label_replace).text =
            HtmlCompat.fromHtml(TR.browsingReplaceWith(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.label_in).text =
            HtmlCompat.fromHtml(TR.browsingIn(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<CheckBox>(R.id.check_only_selected_notes).text = TR.browsingSelectedNotesOnly()
        findViewById<CheckBox>(R.id.check_ignore_case).text = TR.browsingIgnoreCase()
        findViewById<CheckBox>(R.id.check_input_as_regex).text =
            TR.browsingTreatInputAsRegularExpression()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            (dialog as? AlertDialog)?.positiveButton?.isEnabled = false
            contentViewsGroup?.isVisible = false
            loadingViewsGroup?.isVisible = true
            val noteIds = browserViewModel.queryAllSelectedNoteIds()
            onlySelectedNotes?.isChecked = noteIds.isNotEmpty()
            onlySelectedNotes?.isEnabled = noteIds.isNotEmpty()
            val fieldsNames =
                buildList {
                    add(
                        TR.browsingAllFields().toSentenceCase(
                            this@FindAndReplaceDialogFragment,
                            R.string.sentence_all_fields,
                        ),
                    )
                    add(TR.editingTags())
                    addAll(withCol { fieldNamesForNoteIds(noteIds) })
                }
            fieldSelector?.adapter =
                ArrayAdapter(
                    requireActivity(),
                    android.R.layout.simple_spinner_item,
                    fieldsNames,
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            loadingViewsGroup?.isVisible = false
            contentViewsGroup?.isVisible = true
            (dialog as? AlertDialog)?.positiveButton?.isEnabled = true
        }
    }

    // https://github.com/ankitects/anki/blob/64ca90934bc26ddf7125913abc9dd9de8cb30c2b/qt/aqt/browser/find_and_replace.py#L118
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun startFindReplace() {
        val search = dialog?.findViewById<EditText>(R.id.input_search)?.text
        val replacement = dialog?.findViewById<EditText>(R.id.input_replace)?.text
        if (search.isNullOrEmpty() || replacement == null) return
        val onlyInSelectedNotes = onlySelectedNotes?.isChecked ?: true
        val ignoreCase =
            dialog?.findViewById<CheckBox>(R.id.check_ignore_case)?.isChecked ?: true
        val inputAsRegex =
            dialog?.findViewById<CheckBox>(R.id.check_input_as_regex)?.isChecked ?: false
        val selectedField =
            when (fieldSelector?.selectedItemPosition ?: AdapterView.INVALID_POSITION) {
                AdapterView.INVALID_POSITION -> return
                0 -> ALL_FIELDS_AS_FIELD
                1 -> TAGS_AS_FIELD
                else -> fieldSelector?.selectedItem as? String ?: return
            }
        Timber.i("Sending request to find and replace...")
        setFragmentResult(
            REQUEST_FIND_AND_REPLACE,
            bundleOf(
                ARG_SEARCH to search.toString(),
                ARG_REPLACEMENT to replacement.toString(),
                ARG_FIELD to selectedField,
                ARG_ONLY_SELECTED_NOTES to onlyInSelectedNotes,
                // "Ignore case" checkbox text => when it's checked we pass false to the backend
                ARG_MATCH_CASE to !ignoreCase,
                ARG_REGEX to inputAsRegex,
            ),
        )
    }

    companion object {
        const val TAG = "FindAndReplaceDialogFragment"
        const val REQUEST_FIND_AND_REPLACE = "request_find_and_replace"
        const val ARG_SEARCH = "arg_search"
        const val ARG_REPLACEMENT = "arg_replacement"
        const val ARG_FIELD = "arg_field"
        const val ARG_ONLY_SELECTED_NOTES = "arg_only_selected_notes"
        const val ARG_MATCH_CASE = "arg_match_case"
        const val ARG_REGEX = "arg_regex"

        /**
         * Receiving this value in the result [Bundle] for the [ARG_FIELD] entry means that
         * the user selected "All fields" as the field target for the find and replace action.
         */
        const val ALL_FIELDS_AS_FIELD = "find_and_replace_dialog_fragment_all_fields_as_field"

        /**
         * Receiving this value in the result [Bundle] for the [ARG_FIELD] entry means that
         * the user selected "Tags" as the field target for the find and replace action.
         */
        const val TAGS_AS_FIELD = "find_and_replace_dialog_fragment_tags_as_field"
    }
}

fun CardBrowser.registerFindReplaceHandler(action: (FindReplaceResult) -> Unit) {
    setFragmentResultListener(REQUEST_FIND_AND_REPLACE) { _, bundle ->
        action(
            FindReplaceResult(
                search = bundle.getString(ARG_SEARCH) ?: error("Missing required argument: search"),
                replacement = bundle.getString(ARG_REPLACEMENT) ?: error("Missing required argument: replacement"),
                field = bundle.getString(ARG_FIELD) ?: error("Missing required argument: field"),
                onlyOnSelectedNotes = bundle.getBoolean(ARG_ONLY_SELECTED_NOTES, true),
                matchCase = bundle.getBoolean(ARG_MATCH_CASE, false),
                regex = bundle.getBoolean(ARG_REGEX, false),
            ),
        )
    }
}

data class FindReplaceResult(
    val search: String,
    val replacement: String,
    val field: String,
    val onlyOnSelectedNotes: Boolean,
    val matchCase: Boolean,
    val regex: Boolean,
)
