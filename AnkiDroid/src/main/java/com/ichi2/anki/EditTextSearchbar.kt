package com.ichi2.anki

import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import com.ichi2.ui.FixedEditText
import timber.log.Timber

class EditTextSearchbar(
    private var querySearchbar: SearchView,
    private var targetEditText: FixedEditText,
    private var findNextButton: MenuItem,
    private var findPrevButton: MenuItem,
    private var toggleCaseSensitivityButton: MenuItem
) : TextWatcher, SearchView.OnQueryTextListener {
    private var targetEditTextText: String = ""
    private var targetEditTextSelection: Int = 0

    val caseSensitive: Boolean
        get() = toggleCaseSensitivityButton.isChecked

    fun setupListeners() {
        querySearchbar.setOnQueryTextListener(this)
        targetEditText.addTextChangedListener(this)

        querySearchbar.setOnSearchClickListener { setIconsVisibility(true) }
        querySearchbar.setOnCloseListener { setIconsVisibility(false) }

        targetEditTextText = targetEditText.text.toString()
    }

    private fun setIconsVisibility(visibility: Boolean): Boolean {
        Timber.d("setIconsVisibility")

        findNextButton.isVisible = visibility
        findPrevButton.isVisible = visibility
        toggleCaseSensitivityButton.isVisible = visibility

        return false
    }

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        Timber.d("beforeTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart
    }

    override fun afterTextChanged(editable: Editable?) {
        Timber.d("afterTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart
    }

    override fun onTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        Timber.d("onTextChanged")
        targetEditTextText = targetEditText.text.toString()
        targetEditTextSelection = targetEditText.selectionStart
    }

    override fun onQueryTextChange(query: String?): Boolean {
        /* Do Nothing */
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        // search for matches from targetEditTextSelection to end
        var selectionStart = findNextResult(targetEditTextSelection, query)
        if (selectionStart != -1) {
            targetEditText.requestFocus()
            targetEditText.setSelection(selectionStart, selectionStart + query.length)
        } else {
            // if none found, look for one from start to targetEditTextSelection-1
            selectionStart = findPrevResult(targetEditTextSelection - 1, query)
            if (selectionStart != -1) {
                targetEditText.requestFocus()
                targetEditText.setSelection(selectionStart, selectionStart + query.length)
            }
        }
        return true
    }

    private fun findNextResult(from: Int, query: String): Int {
        Timber.i("findNextResult -> targetEditTextText=$targetEditTextText from=$from query=$query")

        // nothing found
        if (query.isEmpty())
            return -1

        var queryI = 0
        for (targetI in from until targetEditTextText.length) {
            if (queryI == query.length) {
                return targetI - query.length
            }

            val targetChar = targetEditTextText[targetI]
            val queryChar = query[queryI]
            if (targetChar == queryChar) {
                queryI++
            }
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
            if (queryI == -1) {
                return targetI + 1
            }

            val targetChar = targetEditTextText[targetI]
            val queryChar = query[queryI]
            if (targetChar == queryChar) {
                queryI--
            }
        }

        return -1 // nothing found
    }
}
