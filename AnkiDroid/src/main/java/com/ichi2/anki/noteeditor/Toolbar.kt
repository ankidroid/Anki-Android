/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.noteeditor

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.Utils
import com.ichi2.utils.ViewGroupUtils
import com.ichi2.utils.ViewGroupUtils.getAllChildrenRecursive
import timber.log.Timber
import java.util.*
import kotlin.math.ceil

/**
 * Handles the toolbar inside [com.ichi2.anki.NoteEditor]
 *
 * * Handles a number of buttons which arbitrarily format selected text, or insert an item at the cursor
 *    * Text is formatted as HTML
 *    * if a tag with an empty body is inserted, we want the cursor in the middle: `<b>|</b>`
 * * Handles the "default" buttons: [setupDefaultButtons], [displayFontSizeDialog], [displayInsertHeadingDialog]
 * * Handles custom buttons with arbitrary prefixes and suffixes: [mCustomButtons]
 *    * Handles generating the 'icon' for these custom buttons: [createDrawableForString]
 *    * Handles CTRL+ the tag of the button: [onKeyUp]. Allows for Ctrl+1..9 shortcuts
 * * Handles adding a dynamic number of buttons and aligning them into rows: [insertItem]
 *    * And handles whether these should be stacked or scrollable: [shouldScrollToolbar]
 */
class Toolbar : FrameLayout {
    var formatListener: TextFormatListener? = null
    private val mToolbar: LinearLayout
    private val mToolbarLayout: LinearLayout
    /** A list of buttons, typically user-defined which modify text + selection */
    private val mCustomButtons: MutableList<View> = ArrayList()
    private val mRows: MutableList<LinearLayout> = ArrayList()

    /**
     * TODO HACK until API 21 - can be removed once tested.
     *
     * inside NoteEditor: use [insertItem] instead of accessing this
     * and remove [R.id.note_editor_toolbar_button_cloze] from [R.layout.note_editor_toolbar]
     */
    var clozeIcon: View? = null
        private set
    private var mStringPaint: Paint? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        LayoutInflater.from(context).inflate(R.layout.note_editor_toolbar, this, true)
        mStringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dpToPixels(24).toFloat()
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        mToolbar = findViewById(R.id.editor_toolbar_internal)
        mToolbarLayout = findViewById(R.id.toolbar_layout)
        clozeIcon = findViewById(R.id.note_editor_toolbar_button_cloze)
        setupDefaultButtons()
    }

    /** Sets up the "standard" buttons to insert bold, italics etc... */
    private fun setupDefaultButtons() {
        // sets up a button click to wrap text with the prefix/suffix. So "aa" becomes "<b>aa</b>"
        fun setupButtonWrappingText(@IdRes id: Int, prefix: String, suffix: String) =
            findViewById<View>(id).setOnClickListener { onFormat(TextWrapper(prefix, suffix)) }

        setupButtonWrappingText(R.id.note_editor_toolbar_button_bold, "<b>", "</b>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_italic, "<em>", "</em>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_underline, "<u>", "</u>")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_insert_mathjax, "\\(", "\\)")
        setupButtonWrappingText(R.id.note_editor_toolbar_button_horizontal_rule, "<hr>", "")
        findViewById<View>(R.id.note_editor_toolbar_button_font_size).setOnClickListener { displayFontSizeDialog() }
        findViewById<View>(R.id.note_editor_toolbar_button_title).setOnClickListener { displayInsertHeadingDialog() }
    }

    /**
     * If a button is assigned a tag, Ctrl+Tag will invoke the button
     * Typically used for Ctrl + 1..9 with custom buttons
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // hack to see if only CTRL is pressed - might not be perfect.
        // I'll avoid checking "function" here as it may be required to press Ctrl
        if (!event.isCtrlPressed || event.isAltPressed || event.isShiftPressed || event.isMetaPressed) {
            return false
        }
        val c: Char = try {
            event.getUnicodeChar(0).toChar()
        } catch (e: Exception) {
            Timber.w(e)
            return false
        }
        if (c == '\u0000') {
            return false
        }
        val expected = c.toString()
        for (v in getAllChildrenRecursive(this)) {
            if (Utils.equals(expected, v.tag)) {
                Timber.i("Handling Ctrl + %s", c)
                v.performClick()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun dpToPixels(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    fun insertItem(@IdRes id: Int, @DrawableRes drawable: Int, runnable: Runnable): AppCompatImageButton {
        // we use the light theme here to ensure the tint is black on both
        // A null theme can be passed after colorControlNormal is defined (API 25)
        val themeContext: Context = ContextThemeWrapper(context, R.style.Theme_Light_Compat)
        val d = VectorDrawableCompat.create(context.resources, drawable, themeContext.theme)
        return insertItem(id, d, runnable)
    }

    fun insertItem(id: Int, drawable: Drawable?, formatter: TextFormatter): View {
        return insertItem(id, drawable, Runnable { onFormat(formatter) })
    }

    fun insertItem(@IdRes id: Int, drawable: Drawable?, runnable: Runnable): AppCompatImageButton {
        val context = context
        val button = AppCompatImageButton(context)
        button.id = id
        button.background = drawable

        /*
            Style didn't work
            int buttonStyle = R.style.note_editor_toolbar_button;
            ContextThemeWrapper context = new ContextThemeWrapper(getContext(), buttonStyle);
            AppCompatImageButton button = new AppCompatImageButton(context, null, buttonStyle);
        */

        // apply style
        val margin = dpToPixels(8)
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        params.setMargins(margin, margin / 2, margin, margin / 2)
        button.layoutParams = params
        val twoDp = ceil((2 / context.resources.displayMetrics.density).toDouble()).toInt()
        button.setPadding(twoDp, twoDp, twoDp, twoDp)
        // end apply style
        if (shouldScrollToolbar()) {
            mToolbar.addView(button, mToolbar.childCount)
        } else {
            addViewToToolbar(button)
        }
        mCustomButtons.add(button)
        button.setOnClickListener { runnable.run() }

        // Hack - items are truncated from the scrollview
        val v = findViewById<View>(R.id.toolbar_layout)
        val expectedWidth = getVisibleItemCount(mToolbar) * dpToPixels(48)
        val width = screenWidth
        val p = LayoutParams(v.layoutParams)
        p.gravity = Gravity.CENTER_VERTICAL or if (expectedWidth > width) Gravity.START else Gravity.CENTER_HORIZONTAL
        v.layoutParams = p
        return button
    }

    @Suppress("DEPRECATION")
    private val screenWidth: Int
        get() {
            val displayMetrics = DisplayMetrics()
            (context as Activity).windowManager
                .defaultDisplay
                .getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }

    /** Clears all items added by [insertItem] */
    fun clearCustomItems() {
        for (v in mCustomButtons) {
            (v.parent as ViewGroup).removeView(v)
        }
        mCustomButtons.clear()
    }

    /**
     * Displays a dialog which allows the HTML size codes to be inserted around text (xx-small to xx-large)
     *
     * @see [R.array.html_size_codes]
     */
    private fun displayFontSizeDialog() {
        val results = resources.getStringArray(R.array.html_size_codes)

        // Might be better to add this as a fragment - let's see.
        MaterialDialog.Builder(context)
            .items(R.array.html_size_code_labels)
            .itemsCallback { _: MaterialDialog?, _: View?, pos: Int, _: CharSequence? ->
                val formatter = TextWrapper(
                    prefix = "<span style=\"font-size:${results[pos]}\">",
                    suffix = "</span>"
                )
                onFormat(formatter)
            }
            .title(R.string.menu_font_size)
            .show()
    }

    /**
     * Displays a dialog which allows `<h1>` to `<h6>` to be inserted
     */
    private fun displayInsertHeadingDialog() {
        MaterialDialog.Builder(context)
            .items("h1", "h2", "h3", "h4", "h5")
            .itemsCallback { _: MaterialDialog?, _: View?, _: Int, string: CharSequence ->
                val formatter = TextWrapper(prefix = "<$string>", suffix = "</$string>")
                onFormat(formatter)
            }
            .title(R.string.insert_heading)
            .show()
    }

    /** Given a string [text], generates a [Drawable] which can be used as a button icon */
    fun createDrawableForString(text: String): Drawable {
        val baseline = -mStringPaint!!.ascent()
        val size = (baseline + mStringPaint!!.descent() + 0.5f).toInt()
        val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawText(text, size / 2f, baseline, mStringPaint!!)
        return BitmapDrawable(resources, image)
    }

    /** Returns the number of top-level children of [layout] that are visible */
    private fun getVisibleItemCount(layout: LinearLayout): Int =
        ViewGroupUtils.getAllChildren(layout).count { it.visibility == VISIBLE }

    private fun addViewToToolbar(button: AppCompatImageButton) {
        val expectedWidth = getVisibleItemCount(mToolbar) * dpToPixels(48)
        val width = screenWidth
        if (expectedWidth <= width) {
            mToolbar.addView(button, mToolbar.childCount)
            return
        }
        var spaceLeft = false
        if (mRows.isNotEmpty()) {
            val row = mRows.last()
            val expectedRowWidth = getVisibleItemCount(row) * dpToPixels(48)
            if (expectedRowWidth <= width) {
                row.addView(button, row.childCount)
                spaceLeft = true
            }
        }
        if (!spaceLeft) {
            val row = LinearLayout(context)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            row.layoutParams = params
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(button)
            mRows.add(row)
            mToolbarLayout.addView(mRows.last())
        }
    }

    /**
     * If a [formatListener] is attached, supply it with the provided [TextFormatter] so that
     * the current selection of text can be formatted, and the selection can be changed.
     *
     * The listener determines the appropriate selection of text to be formatted and handles
     * selection changes
     */
    fun onFormat(formatter: TextFormatter) {
        formatListener?.performFormat(formatter)
    }

    fun setIconColor(@ColorInt color: Int) {
        ViewGroupUtils.getAllChildren(mToolbar)
            .forEach { (it as AppCompatImageButton).setColorFilter(color) }
        mStringPaint!!.color = color
    }

    /** @see performFormat */
    fun interface TextFormatListener {
        /**
         * A function which accepts a [TextFormatter] and performs some formatting, handling selection changes
         * In the note editor: this takes the [TextFormatter], determines the correct EditText and selection,
         * applies the [TextFormatter] to the selection, and ensures the selection is valid
         *
         * We use a [TextFormatter] to ensure that the selection is correct after the modification
         */
        fun performFormat(formatter: TextFormatter)
    }

    /**
     * A function which takes and returns a [StringFormat] structure
     * Providing a method of inserting text and knowledge of how the selection should change
     */
    fun interface TextFormatter {
        /**
         * A function which takes and returns a [StringFormat] structure
         * Providing a method of inserting text and knowledge of how the selection should change
         */
        fun format(s: String): StringFormat
    }

    /**
     * A [TextFormatter] which wraps the selected string with [prefix] and [suffix]
     * If there's no selected, the cursor is in the middle of the prefix and suffix
     * If there is text selected, the whole string is selected
     */
    class TextWrapper(private val prefix: String, private val suffix: String) : TextFormatter {
        override fun format(s: String): StringFormat {
            return StringFormat(result = prefix + s + suffix).apply {
                if (s.isEmpty()) {
                    // if there's no selection: place the cursor between the start and end tag
                    selectionStart = prefix.length
                    selectionEnd = prefix.length
                } else {
                    // otherwise, wrap the newly formatted context
                    selectionStart = 0
                    selectionEnd = result.length
                }
            }
        }
    }

    /**
     * Defines a string insertion, and the selection which should occur once the string is inserted
     *
     * @param result The string which should be inserted
     *
     * @param selectionStart
     * The number of characters inside [result] where the selection should start
     * For example: in {{c1::}}, we should set this to 6, to start after the ::
     *
     * @param selectionEnd
     * The number of character inside [result] where the selection should end
     * If the input was empty, we typically want this between the start and end tags
     * If not, at the end of the string
     */
    data class StringFormat(
        @JvmField var result: String = "",
        @JvmField var selectionStart: Int = 0,
        @JvmField var selectionEnd: Int = 0
    )

    companion object {
        /** @return true: toolbar should scroll horizontally. false: toolbar should be stacked vertically */
        fun shouldScrollToolbar(): Boolean {
            return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("noteEditorScrollToolbar", true)
        }
    }
}
