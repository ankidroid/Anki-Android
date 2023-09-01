/****************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.ClipboardUtil.IMAGE_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.getImageUri
import com.ichi2.utils.ClipboardUtil.getPlainText
import com.ichi2.utils.ClipboardUtil.hasImage
import com.ichi2.utils.KotlinCleanup
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.*
import kotlin.math.max
import kotlin.math.min

class FieldEditText : FixedEditText, NoteService.NoteField {
    override var ord = 0
    private var mOrigBackground: Drawable? = null
    private var mSelectionChangeListener: TextSelectionListener? = null
    private var mImageListener: ImagePasteListener? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var clipboard: ClipboardManager? = null

    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attr: AttributeSet?) : super(context!!, attr)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context!!, attrs, defStyle)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shouldDisableExtendedTextUi()) {
            Timber.i("Disabling Extended Text UI")
            this.imeOptions = this.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
    }

    private fun shouldDisableExtendedTextUi(): Boolean {
        return this.context.sharedPrefs().getBoolean("disableExtendedTextUi", false)
    }

    @KotlinCleanup("Simplify")
    override val fieldText: String?
        get() {
            val text = text ?: return null
            return text.toString()
        }

    fun init() {
        try {
            clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        } catch (e: Exception) {
            Timber.w(e)
        }
        minimumWidth = 400
        mOrigBackground = background
        // Fixes bug where new instances of this object have wrong colors, probably
        // from some reuse mechanic in Android.
        setDefaultStyle()
    }

    fun setImagePasteListener(imageListener: ImagePasteListener?) {
        mImageListener = imageListener
    }

    @KotlinCleanup("add extension method to iterate clip items")
    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(editorInfo) ?: return null
        EditorInfoCompat.setContentMimeTypes(editorInfo, IMAGE_MIME_TYPES)
        ViewCompat.setOnReceiveContentListener(
            this,
            IMAGE_MIME_TYPES,
            object : OnReceiveContentListener {
                override fun onReceiveContent(view: View, payload: ContentInfoCompat): ContentInfoCompat? {
                    val pair = payload.partition { item -> item.uri != null }
                    val uriContent = pair.first
                    val remaining = pair.second

                    if (mImageListener == null || uriContent == null) {
                        return remaining
                    }

                    val clip = uriContent.clip
                    val description = clip.description

                    if (!hasImage(description)) {
                        return remaining
                    }

                    for (i in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(i).uri
                        try {
                            onImagePaste(uri)
                        } catch (e: Exception) {
                            Timber.w(e)
                            CrashReportService.sendExceptionReport(e, "NoteEditor::onImage")
                            return remaining
                        }
                    }

                    return remaining
                }
            }
        )

        return InputConnectionCompat.createWrapper(this, inputConnection, editorInfo)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        if (mSelectionChangeListener != null) {
            try {
                mSelectionChangeListener!!.onSelectionChanged(selStart, selEnd)
            } catch (e: Exception) {
                Timber.w(e, "mSelectionChangeListener")
            }
        }
        super.onSelectionChanged(selStart, selEnd)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun setHintLocale(locale: Locale) {
        Timber.d("Setting hint locale to '%s'", locale)
        imeHintLocales = LocaleList(locale)
    }

    /**
     * Modify the style of this view to represent a duplicate field.
     */
    fun setDupeStyle() {
        setBackgroundColor(getColorFromAttr(context, R.attr.duplicateColor))
    }

    /**
     * Restore the default style of this view.
     */
    fun setDefaultStyle() {
        background = mOrigBackground
    }

    fun setContent(content: String?, replaceNewLine: Boolean) {
        val text = if (content == null) {
            ""
        } else if (replaceNewLine) {
            content.replace("<br(\\s*/*)>".toRegex(), NEW_LINE)
        } else {
            content
        }
        setText(text)
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = super.onSaveInstanceState()
        return SavedState(state, ord)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        // This handles both CTRL+V and "Paste"
        if (id == android.R.id.paste) {
            if (hasImage(clipboard)) {
                return onImagePaste(getImageUri(clipboard))
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
            setText(
                text!!.substring(0, start) + pasted + text!!.substring(end)
            )
            setSelection(start + pasted.length)
            return true
        }
        return false
    }

    private fun onImagePaste(imageUri: Uri?): Boolean {
        return if (imageUri == null) {
            false
        } else {
            mImageListener!!.onImagePaste(this, imageUri)
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
        get() = this.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES == InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

    @Parcelize
    internal class SavedState(val state: Parcelable?, val ord: Int) : BaseSavedState(state)

    interface TextSelectionListener {
        fun onSelectionChanged(selStart: Int, selEnd: Int)
    }

    fun interface ImagePasteListener {
        fun onImagePaste(editText: EditText, uri: Uri?): Boolean
    }

    companion object {
        val NEW_LINE: String = System.getProperty("line.separator")!!
    }
}
