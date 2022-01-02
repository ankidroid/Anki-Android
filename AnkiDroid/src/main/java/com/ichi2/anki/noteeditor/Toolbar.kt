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
import com.ichi2.utils.ViewGroupUtils.getAllChildrenRecursive
import timber.log.Timber
import java.util.*
import kotlin.math.ceil

class Toolbar : FrameLayout {
    private var mFormatCallback: TextFormatListener? = null
    private var mToolbar: LinearLayout? = null
    private var mToolbarLayout: LinearLayout? = null
    private val mCustomButtons: MutableList<View> = ArrayList()
    private val mRows: MutableList<LinearLayout> = ArrayList()

    // HACK until API 21 FIXME can this be altered now?
    var clozeIcon: View? = null
        private set
    private var mStringPaint: Paint? = null

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        LayoutInflater.from(context).inflate(R.layout.note_editor_toolbar, this, true)
        val paintSize = dpToPixels(24)
        mStringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mStringPaint!!.textSize = paintSize.toFloat()
        mStringPaint!!.color = Color.BLACK
        mStringPaint!!.textAlign = Paint.Align.CENTER
        mToolbar = findViewById(R.id.editor_toolbar_internal)
        mToolbarLayout = findViewById(R.id.toolbar_layout)
        setClick(R.id.note_editor_toolbar_button_bold, "<b>", "</b>")
        setClick(R.id.note_editor_toolbar_button_italic, "<em>", "</em>")
        setClick(R.id.note_editor_toolbar_button_underline, "<u>", "</u>")
        setClick(R.id.note_editor_toolbar_button_insert_mathjax, "\\(", "\\)")
        setClick(R.id.note_editor_toolbar_button_horizontal_rule, "<hr>", "")
        findViewById<View>(R.id.note_editor_toolbar_button_font_size).setOnClickListener { displayFontSizeDialog() }
        findViewById<View>(R.id.note_editor_toolbar_button_title).setOnClickListener { displayInsertHeadingDialog() }
        clozeIcon = findViewById(R.id.note_editor_toolbar_button_cloze)
    }

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

    fun insertItem(id: Int, drawable: Drawable?, formatter: TextFormatter?): View {
        return insertItem(id, drawable) { onFormat(formatter) }
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
            mToolbar!!.addView(button, mToolbar!!.childCount)
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

    fun clearCustomItems() {
        for (v in mCustomButtons) {
            (v.parent as ViewGroup).removeView(v)
        }
        mCustomButtons.clear()
    }

    fun setFormatListener(formatter: TextFormatListener?) {
        mFormatCallback = formatter
    }

    private fun displayFontSizeDialog() {
        val results = resources.getStringArray(R.array.html_size_codes)

        // Might be better to add this as a fragment - let's see.
        MaterialDialog.Builder(context)
            .items(R.array.html_size_code_labels)
            .itemsCallback { _: MaterialDialog?, _: View?, pos: Int, _: CharSequence? ->
                val prefix = "<span style=\"font-size:" + results[pos] + "\">"
                val suffix = "</span>"
                val formatter = TextWrapper(prefix, suffix)
                onFormat(formatter)
            }
            .title(R.string.menu_font_size)
            .show()
    }

    private fun displayInsertHeadingDialog() {
        MaterialDialog.Builder(context)
            .items("h1", "h2", "h3", "h4", "h5")
            .itemsCallback { _: MaterialDialog?, _: View?, _: Int, string: CharSequence ->
                val prefix = "<$string>"
                val suffix = "</$string>"
                val formatter = TextWrapper(prefix, suffix)
                onFormat(formatter)
            }
            .title(R.string.insert_heading)
            .show()
    }

    fun createDrawableForString(text: String?): Drawable {
        val baseline = -mStringPaint!!.ascent()
        val size = (baseline + mStringPaint!!.descent() + 0.5f).toInt()
        val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawText(text!!, size / 2f, baseline, mStringPaint!!)
        return BitmapDrawable(resources, image)
    }

    private fun getVisibleItemCount(layout: LinearLayout?): Int {
        var count = 0
        for (i in 0 until layout!!.childCount) {
            if (layout.getChildAt(i).visibility == VISIBLE) {
                count++
            }
        }
        return count
    }

    private fun addViewToToolbar(button: AppCompatImageButton) {
        val expectedWidth = getVisibleItemCount(mToolbar) * dpToPixels(48)
        val width = screenWidth
        if (expectedWidth <= width) {
            mToolbar!!.addView(button, mToolbar!!.childCount)
            return
        }
        var spaceLeft = false
        if (mRows.isNotEmpty()) {
            val row = mRows[mRows.size - 1]
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
            mToolbarLayout!!.addView(mRows[mRows.size - 1])
        }
    }

    private fun setClick(@IdRes id: Int, prefix: String, suffix: String) {
        setClick(id, TextWrapper(prefix, suffix))
    }

    private fun setClick(id: Int, textWrapper: TextFormatter) {
        findViewById<View>(id).setOnClickListener { onFormat(textWrapper) }
    }

    fun onFormat(formatter: TextFormatter?) {
        if (mFormatCallback == null) {
            return
        }
        mFormatCallback!!.performFormat(formatter)
    }

    fun setIconColor(@ColorInt color: Int) {
        for (i in 0 until mToolbar!!.childCount) {
            val button = mToolbar!!.getChildAt(i) as AppCompatImageButton
            button.setColorFilter(color)
        }
        mStringPaint!!.color = color
    }

    interface TextFormatListener {
        fun performFormat(formatter: TextFormatter?)
    }

    interface TextFormatter {
        fun format(s: String): TextWrapper.StringFormat
    }

    class TextWrapper(private val prefix: String, private val suffix: String) : TextFormatter {
        override fun format(s: String): StringFormat {
            val stringFormat = StringFormat()
            stringFormat.result = prefix + s + suffix
            if (s.isEmpty()) {
                stringFormat.start = prefix.length
                stringFormat.end = prefix.length
            } else {
                stringFormat.start = 0
                stringFormat.end = stringFormat.result!!.length
            }
            return stringFormat
        }

        class StringFormat {
            @JvmField
            var result: String? = null
            @JvmField
            var start = 0
            @JvmField
            var end = 0
        }
    }

    companion object {
        fun shouldScrollToolbar(): Boolean {
            return AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance()).getBoolean("noteEditorScrollToolbar", true)
        }
    }
}
