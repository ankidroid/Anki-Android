package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText

interface AutoFocusable {
    fun autoFocus(editText: EditText) {
        editText.requestFocus()
        editText.selectAll() // todo may change
    }
}

@Suppress("deprecation")
open class AutoFocusEditTextPreference : android.preference.EditTextPreference, AutoFocusable {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    override fun onBindView(view: View?) {
        super.onBindView(view)
        autoFocus(editText)
    }
}
