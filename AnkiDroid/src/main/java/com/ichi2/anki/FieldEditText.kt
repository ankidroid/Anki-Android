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
import android.os.*
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.annotation.RequiresApi
import androidx.core.view.ContentInfoCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.ClipboardUtil.IMAGE_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.getImageUri
import com.ichi2.utils.ClipboardUtil.hasImage
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.lang.Exception
import java.util.*

class FieldEditText : FixedEditText, NoteService.NoteField {
    override var ord = 0
    private var mOrigBackground: Drawable? = null
    private var mSelectionChangeListener: TextSelectionListener? = null
    private var mImageListener: ImagePasteListener? = null
    private var mClipboard: ClipboardManager? = null

    constructor(context: Context?) : super(context!!) {}
    constructor(context: Context?, attr: AttributeSet?) : super(context!!, attr) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context!!, attrs, defStyle) {}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (shouldDisableExtendedTextUi()) {
            Timber.i("Disabling Extended Text UI")
            this.imeOptions = this.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
    }

    @KotlinCleanup("Remove try-catch")
    private fun shouldDisableExtendedTextUi(): Boolean {
        return try {
            val sp = AnkiDroidApp.getSharedPrefs(this.context)
            sp.getBoolean("disableExtendedTextUi", false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get extended UI preference")
            false
        }
    }

    @KotlinCleanup("Simplify")
    override val fieldText: String?
        get() {
            val text = text ?: return null
            return text.toString()
        }

    fun init() {
        try {
            mClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
            this, IMAGE_MIME_TYPES,
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
                            AnkiDroidApp.sendExceptionReport(e, "NoteEditor::onImage")
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
    @Suppress("deprecation")
    fun setDefaultStyle() {
        setBackgroundDrawable(mOrigBackground)
    }

    fun setSelectionChangeListener(listener: TextSelectionListener?) {
        mSelectionChangeListener = listener
    }

    fun setContent(content: String?, replaceNewLine: Boolean) {
        var _content = content
        if (content == null) {
            _content = ""
        } else if (replaceNewLine) {
            _content = content.replace("<br(\\s*/*)>".toRegex(), NEW_LINE)
        }
        setText(_content)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val state = super.onSaveInstanceState()
        val savedState = SavedState(state)
        savedState.ord = ord
        return savedState
    }

    @KotlinCleanup("Return instead of using _id")
    override fun onTextContextMenuItem(id: Int): Boolean {
        // This handles both CTRL+V and "Paste"
        var _id = id
        if (id == android.R.id.paste && hasImage(mClipboard)) {
            return onImagePaste(getImageUri(mClipboard))
        }

        /*
        * The following code replaces the instruction "paste with formatting" with "paste as plan text"
        * Since AnkiDroid does not know how to use formatted text and strips the formatting when saving the note,
        * it ensures that the user sees the exact plain text which is actually being saved, not the formatted text.
        */

        // https://stackoverflow.com/a/45319485
        if (id == android.R.id.paste) {
            /**
             * Modified StackOverflow answer:
             * Pasting as plain text for VERSION_CODES < M required modifying
             * the user's clipboard, and hence has not been used.
             *
             * During testing, older devices pasted text as plain text by default,
             * so there was no need for a TO-DO
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                _id = android.R.id.pasteAsPlainText
            }
        }
        return super.onTextContextMenuItem(_id)
    }

    @KotlinCleanup("Make param non-null")
    protected fun onImagePaste(imageUri: Uri?): Boolean {
        return if (imageUri == null) {
            false
        } else mImageListener!!.onImagePaste(this, imageUri)
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        val ss = state
        super.onRestoreInstanceState(ss.superState)
        ord = ss.ord
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

    @KotlinCleanup("Use @Parcelize")
    internal class SavedState : BaseSavedState {
        var ord = 0

        constructor(superState: Parcelable?) : super(superState) {}

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(ord)
        }

        private constructor(`in`: Parcel) : super(`in`) {
            ord = `in`.readInt()
        }

        companion object {
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel): SavedState {
                    return SavedState(source)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

    interface TextSelectionListener {
        fun onSelectionChanged(selStart: Int, selEnd: Int)
    }

    interface ImagePasteListener {
        fun onImagePaste(editText: EditText?, uri: Uri?): Boolean
    }

    companion object {
        @JvmField
        val NEW_LINE: String = Objects.requireNonNull(System.getProperty("line.separator"))
    }
}
