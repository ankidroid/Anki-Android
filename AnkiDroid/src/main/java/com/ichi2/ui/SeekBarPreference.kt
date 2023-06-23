//noinspection MissingCopyrightHeader #8659
/* The following code was written by Matthew Wiggins
 * and is released under the APACHE 2.0 license
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * adjusted by Norbert Nagold 2011 <norbert.nagold@gmail.com>
 */
package com.ichi2.ui

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.StringRes
import com.ichi2.anki.AnkiDroidApp

@Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019 see: SeekBarPreferenceCompat
class SeekBarPreference(context: Context, attrs: AttributeSet) : android.preference.DialogPreference(context, attrs), OnSeekBarChangeListener {
    private lateinit var mSeekLine: LinearLayout
    private lateinit var mSeekBar: SeekBar
    private lateinit var mValueText: TextView
    private val mSuffix: String? = attrs.getAttributeValue(androidns, "text")
    private val mDefault: Int = attrs.getAttributeIntValue(androidns, "defaultValue", 0)
    private val mMax: Int = attrs.getAttributeIntValue(androidns, "max", 100)
    private val mMin: Int = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0)
    private val mInterval: Int = attrs.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1)
    private var mValue = 0

    @StringRes
    private val mXLabel: Int = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "xLabel", 0)

    @StringRes
    private val mYLabel: Int = attrs.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "yLabel", 0)

    override fun onCreateDialogView(): View {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)
        mValueText = FixedTextView(context)
        mValueText.gravity = Gravity.CENTER_HORIZONTAL
        mValueText.textSize = 32f
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(mValueText, params)
        mSeekBar = SeekBar(context)
        mSeekBar.setOnSeekBarChangeListener(this)
        layout.addView(
            mSeekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        if (mXLabel != 0 && mYLabel != 0) {
            val paramsSeekbar = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            paramsSeekbar.setMargins(0, 12, 0, 0)
            mSeekLine = LinearLayout(context)
            mSeekLine.orientation = LinearLayout.HORIZONTAL
            mSeekLine.setPadding(6, 6, 6, 6)
            addLabelsBelowSeekBar()
            layout.addView(mSeekLine, paramsSeekbar)
        }
        if (shouldPersist()) {
            mValue = getPersistedInt(mDefault)
        }
        mSeekBar.max = (mMax - mMin) / mInterval
        mSeekBar.progress = (mValue - mMin) / mInterval
        val t = mValue.toString()
        mValueText.text = if (mSuffix == null) t else t + mSuffix
        return layout
    }

    override fun onBindDialogView(v: View) {
        super.onBindDialogView(v)
        mSeekBar.max = (mMax - mMin) / mInterval
        mSeekBar.progress = (mValue - mMin) / mInterval
    }

    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restore, defaultValue)
        mValue = getPersistedInt(mDefault)
        mValue = if (restore) {
            if (shouldPersist()) getPersistedInt(mDefault) else 0
        } else {
            defaultValue as Int
        }
    }

    override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
        if (fromTouch) {
            mValue = value * mInterval + mMin
            val t = mValue.toString()
            mValueText.text = if (mSuffix == null) t else t + mSuffix
            onValueUpdated()
        }
    }

    private fun onValueUpdated() {
        if (shouldPersist()) {
            persistInt(mValue)
        }
        callChangeListener(mValue)
    }

    var value: Int
        get() = if (mValue == 0) {
            getPersistedInt(mDefault)
        } else {
            mValue
        }
        set(value) {
            mValue = value
            persistInt(value)
        }

    override fun onStartTrackingTouch(seek: SeekBar) {}
    override fun onStopTrackingTouch(seek: SeekBar) {
        this.dialog.dismiss()
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setNegativeButton(null, null)
        builder.setPositiveButton(null, null)
        builder.setTitle(null)
    }

    private fun addLabelsBelowSeekBar() {
        val labels = intArrayOf(mXLabel, mYLabel)
        for (count in 0..1) {
            val textView: TextView = FixedTextView(context)
            textView.text = context.getString(labels[count])
            textView.gravity = Gravity.START
            mSeekLine.addView(textView)
            if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) textView.layoutParams = if (count == 1) getLayoutParams(0.0f) else getLayoutParams(1.0f) else textView.layoutParams = if (count == 0) getLayoutParams(0.0f) else getLayoutParams(1.0f)
        }
    }

    fun getLayoutParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            weight
        )
    }

    companion object {
        private const val androidns = "http://schemas.android.com/apk/res/android"
    }
}
