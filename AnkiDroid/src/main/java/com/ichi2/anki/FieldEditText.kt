/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 * Licensed under GPLv3 or later.
 ****************************************************************************************/

package com.ichi2.anki

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.LocaleList
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.CollectionHelper
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.ClipboardUtil.getDescription
import com.ichi2.utils.ClipboardUtil.getPlainText
import com.ichi2.utils.ClipboardUtil.getUri
import com.ichi2.utils.ClipboardUtil.hasMedia
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FieldEditText :
    FixedEditText,
    NoteService.NoteField {

    override var ord = 0
    private var origBackground: Drawable? = null
    private var selectionChangeListener: TextSelectionListener? = null
    private var pasteListener: PasteListener? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var clipboard: ClipboardManager? = null

    private var lastCutTime: Long = 0
    private var isHandlingCut = false

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attr: AttributeSet?) : super(context!!, attr)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context!!, attrs, defStyle)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shouldDisableExtendedTextUi()) {
            Timber.i("Disabling Extended Text UI")
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
    }

    private fun shouldDisableExtendedTextUi(): Boolean =
        context.sharedPrefs().getBoolean("disableExtendedTextUi", false)

    @KotlinCleanup("Simplify")
    override val fieldText: String?
        get() = text?.toString()

    fun init() {
        try {
            clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }

        minimumWidth = 400
        origBackground = background
        setDefaultStyle()

        val highlightColor = MaterialColors.getColor(
            context,
            R.attr.editTextHighlightColor,
            "#99CCFF".toColorInt(),
        )
        setHighlightColor(highlightColor)
    }

    fun setPasteListener(listener: PasteListener) {
        pasteListener = listener
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        try {
            selectionChangeListener?.onSelectionChanged(selStart, selEnd)
        } catch (e: Exception) {
            Timber.w(e, "mSelectionChangeListener")
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    fun setHintLocale(locale: Locale) {
        imeHintLocales = LocaleList(locale)
    }

    fun setDupeStyle() {
        setBackgroundColor(MaterialColors.getColor(context, R.attr.duplicateColor, 0))
    }

    fun setDefaultStyle() {
        background = origBackground
    }

    fun setContent(content: String?, replaceNewLine: Boolean) {
        val text = when {
            content == null -> ""
            replaceNewLine -> content.replace("<br(\\s*/*)>".toRegex(), NEW_LINE)
            else -> content
        }
        setText(text)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        // Handle Ctrl+X (cut)
        if (keyCode == KeyEvent.KEYCODE_X && event?.isCtrlPressed == true) {
            val selStart = selectionStart
            val selEnd = selectionEnd

            if (selStart != selEnd) {
                try {
                    val start = min(selStart, selEnd)
                    val end = max(selStart, selEnd)
                    val editable = editableText

                    val cutText = editable.subSequence(start, end)
                    val clip = android.content.ClipData.newPlainText("cut", cutText)
                    clipboard?.setPrimaryClip(clip)

                    editable.delete(start, end)

                    isHandlingCut = true

                    // Force editor refresh
                    if (start > 0) {
                        setSelection(start - 1)
                        setSelection(start)
                    } else if (editable.length > start) {
                        setSelection(start + 1)
                        setSelection(start)
                    } else {
                        setSelection(start)
                    }

                    lastCutTime = CollectionHelper.getInstance().col.time().currentTimeMillis()
                    isHandlingCut = false
                    return true

                } catch (e: Exception) {
                    Timber.w(e, "Manual cut failed")
                    isHandlingCut = false
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        // Fix backspace after cut
        val now = CollectionHelper.getInstance().col.time().currentTimeMillis()
        if (keyCode == KeyEvent.KEYCODE_DEL && !isHandlingCut && now - lastCutTime < 100) {
            val cursor = selectionStart
            if (selectionStart != selectionEnd) {
                setSelection(cursor)
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState(), ord)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste) {
            if (hasMedia(clipboard)) {
                return onPaste(getUri(clipboard), getDescription(clipboard))
            }
            return pastePlainText()
        }
        return super.onTextContextMenuItem(id)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun pastePlainText(): Boolean {
        getPlainText(clipboard, context)?.let { pasted ->
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            setText(text!!.substring(0, start) + pasted + text!!.substring(end))
            setSelection(start + pasted.length)
            return true
        }
        return false
    }

    private fun onPaste(mediaUri: Uri?, description: ClipDescription?): Boolean {
        if (mediaUri == null) return false

        return try {
            pasteListener?.onPaste(this, mediaUri, description) ?: false
        } catch (e: Exception) {
            Timber.w(e, "Failed to paste media")
            showSnackbar(context.getString(R.string.multimedia_editor_something_wrong))
            false
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        ord = state.ord
    }

    fun setCapitalize(value: Boolean) {
        val inputType = this.inputType
        this.inputType = if (value) {
            inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES.inv()
        }
    }

    val isCapitalized: Boolean
        get() = inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES ==
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    @Parcelize
    internal class SavedState(
        val state: Parcelable?,
        val ord: Int,
    ) : BaseSavedState(state)

    interface TextSelectionListener {
        fun onSelectionChanged(selStart: Int, selEnd: Int)
    }

    fun interface PasteListener {
        fun onPaste(editText: EditText, uri: Uri?, description: ClipDescription?): Boolean
    }

    companion object {
        val NEW_LINE: String = System.getProperty("line.separator")!!
    }
}
