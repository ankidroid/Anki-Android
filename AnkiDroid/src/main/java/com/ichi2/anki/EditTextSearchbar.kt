package com.ichi2.anki

import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.widget.SearchView
import com.ichi2.ui.FixedEditText
import timber.log.Timber

class EditTextSearchbar(
    private var querySearchbar: SearchView,
    private var targetEditText: FixedEditText
) : TextWatcher, SearchView.OnQueryTextListener {

    fun setupListeners() {
        querySearchbar.setOnQueryTextListener(this)
        targetEditText.addTextChangedListener(this)
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        /* Do nothing */
    }

    override fun afterTextChanged(editable: Editable?) {
        /* Do nothing */
    }

    override fun onTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        Timber.i("mEditorEditText:: onTextChanged -> {$text} {$start} {$count} {$after}")
        // todo
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        Timber.i("mToolbarSearchView:: onQueryTextChange -> {$newText}")
        targetEditText.requestFocus()
        targetEditText.setSelection(3, 5)
        // todo
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        querySearchbar.clearFocus()
        return true
    }
}
