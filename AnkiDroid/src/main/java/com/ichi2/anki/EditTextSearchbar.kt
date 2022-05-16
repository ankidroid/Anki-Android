/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
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

package com.ichi2.anki

import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.appcompat.widget.SearchView
import com.ichi2.ui.FixedEditText
import timber.log.Timber
import kotlin.math.max

class EditTextSearchbar(
    @get:VisibleForTesting(otherwise = PRIVATE)
    var querySearchbar: SearchView,
    @get:VisibleForTesting(otherwise = PRIVATE)
    var targetEditText: FixedEditText,
    private var findNextButton: MenuItem,
    private var findPrevButton: MenuItem,
    private var toggleCaseSensitivityButton: MenuItem
) : TextWatcher, SearchView.OnQueryTextListener {
    private var targetEditTextText: String = ""
    private var targetEditTextSelection: Int = 0

    private var queryText: String = ""

    val caseSensitive: Boolean
        get() = toggleCaseSensitivityButton.isChecked

    fun setupListeners() {
        querySearchbar.setOnQueryTextListener(this)
        targetEditText.addTextChangedListener(this)

        targetEditTextText = targetEditText.text.toString()
    }

    fun nextButtonOnClick(): Boolean {
        Timber.d("nextButtonOnClick")
        targetEditTextSelection = targetEditText.selectionEnd % targetEditTextText.length
        Timber.i("targetEditTextSelection=$targetEditTextSelection")
        return onQueryTextSubmit(queryText)
    }

    fun prevButtonOnClick(): Boolean {
        Timber.d("prevButtonOnClick")
        targetEditTextSelection =
            max((targetEditText.selectionStart) % targetEditTextText.length, 0)
        Timber.i("targetEditTextSelection=$targetEditTextSelection")
        return reverseSearch(queryText)
    }

    fun setIconsVisibility(visibility: Boolean): Boolean {
        findNextButton.isVisible = visibility
        findPrevButton.isVisible = visibility
        toggleCaseSensitivityButton.isVisible = visibility

        return true
    }

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        Timber.d("beforeTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart % targetEditTextText.length
    }

    override fun afterTextChanged(editable: Editable?) {
        Timber.d("afterTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart % targetEditTextText.length
    }

    override fun onTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        Timber.d("onTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart % targetEditTextText.length
    }

    override fun onQueryTextChange(query: String): Boolean {
        queryText = query
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        queryText = query
        // search for matches from targetEditTextSelection to end
        // if none found, look for one from start to targetEditTextSelection
        // if none found, look for one start to end
        if (fromTargetEditTextSelection(query) == -1) {
            if (toTargetEditTextSelection(query) == -1) {
                // if nothing found set cursor at the end of text
                if (fromStartToEnd(query) == -1) {
                    targetEditText.setSelection(targetEditTextText.length)
                    targetEditTextSelection = targetEditText.selectionStart
                }
            }
        }
        return true
    }

    fun reverseSearch(query: String): Boolean {
        queryText = query
        // search for matches from targetEditTextSelection to start
        // if none found, look for one from end to targetEditTextSelection
        // if none found, look for one start to end
        if (toTargetEditTextSelection(query) == -1) {
            targetEditTextSelection = targetEditTextText.length

            if (toTargetEditTextSelection(query) == -1) {
                // if nothing found set cursor at the end of text
                if (fromStartToEnd(query) == -1) {
                    targetEditText.setSelection(targetEditTextText.length)
                    targetEditTextSelection = targetEditText.selectionStart
                }
            }
        }
        return true
    }

    private fun fromTargetEditTextSelection(query: String): Int {
        val selectionStart = findNextResult(targetEditTextSelection, query)
        Timber.i("fromTargetEditTextSelection=$selectionStart")
        if (selectionStart != -1) {
            targetEditText.requestFocus()
            targetEditText.setSelection(selectionStart, selectionStart + query.length)
        }
        return selectionStart
    }

    private fun toTargetEditTextSelection(query: String): Int {
        val selectionStart = findPrevResult(targetEditTextSelection - 1, query)
        Timber.i("toTargetEditTextSelection=$selectionStart")
        if (selectionStart != -1) {
            targetEditText.requestFocus()
            targetEditText.setSelection(selectionStart, selectionStart + query.length)
        }
        return selectionStart
    }

    private fun fromStartToEnd(query: String): Int {
        val selectionStart = findNextResult(0, query)
        Timber.i("fromStartToEnd=$selectionStart")
        if (selectionStart != -1) {
            targetEditText.requestFocus()
            targetEditText.setSelection(selectionStart, selectionStart + query.length)
        }
        return selectionStart
    }

    private fun findNextResult(from: Int, query: String): Int {
        Timber.i("findNextResult -> targetEditTextText=$targetEditTextText from=$from query=$query")

        // nothing found
        if (query.isEmpty())
            return -1

        var queryI = 0
        for (targetI in from until targetEditTextText.length) {
            var targetChar = targetEditTextText[targetI]
            var queryChar = query[queryI]
            if (!caseSensitive) {
                targetChar = targetChar.lowercaseChar()
                queryChar = queryChar.lowercaseChar()
            }
            if (targetChar == queryChar) {
                queryI++
            }
            if (queryI == query.length) {
                return targetI - query.length + 1
            }
            Timber.i("targetI=$targetI queryI=$queryI targetChar=$targetChar queryChar=$queryChar")
        }

        return -1 // nothing found
    }

    private fun findPrevResult(to: Int, query: String): Int {
        Timber.i("findPrevResult -> targetEditTextText=$targetEditTextText to=$to query=$query")

        // nothing found
        if (query.isEmpty())
            return -1

        var queryI = query.length - 1
        for (targetI in to downTo 0) {
            var targetChar = targetEditTextText[targetI]
            var queryChar = query[queryI]
            if (!caseSensitive) {
                targetChar = targetChar.lowercaseChar()
                queryChar = queryChar.lowercaseChar()
            }
            if (targetChar == queryChar) {
                queryI--
            }
            if (queryI == -1) {
                return targetI
            }
            Timber.i("targetI=$targetI queryI=$queryI targetChar=$targetChar queryChar=$queryChar")
        }

        return -1 // nothing found
    }
}
