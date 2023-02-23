/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 *
 *
 *      The following code was written by Matthew Wiggins
 *      and is released under the APACHE 2.0 license
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  adjusted by Norbert Nagold 2011 <norbert.nagold@gmail.com>
 *  adjusted by David Allison 2021 <davidallisongithub@gmail.com>
 *    * Converted to androidx.preference.DialogPreference
 *    * Split into SeekBarPreferenceCompat and SeekBarDialogFragmentCompat
 */
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.withStyledAttributes
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.ui.FixedTextView

class SeekBarPreferenceCompat
@JvmOverloads // required to inflate the preference from a XML
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.dialogPreferenceStyle,
    defStyleRes: Int = R.style.Preference_DialogPreference
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes), DialogFragmentProvider {

    private var suffix: String
    private var default: Int
    private var max: Int
    private var min: Int
    private var interval: Int
    private var mValue = 0

    @StringRes
    private var xLabel: Int

    @StringRes
    private var yLabel: Int

    init {
        suffix = attrs?.getAttributeValue(AnkiDroidApp.ANDROID_NAMESPACE, "text") ?: ""
        default = attrs?.getAttributeIntValue(AnkiDroidApp.ANDROID_NAMESPACE, "defaultValue", 0) ?: 0
        max = attrs?.getAttributeIntValue(AnkiDroidApp.ANDROID_NAMESPACE, "max", 100) ?: 100
        min = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0) ?: 0
        interval = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1) ?: 1
        xLabel = attrs?.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "xlabel", 0) ?: 0
        yLabel = attrs?.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "ylabel", 0) ?: 0

        context.withStyledAttributes(attrs, R.styleable.CustomPreference) {
            val useSimpleSummaryProvider = getBoolean(R.styleable.CustomPreference_useSimpleSummaryProvider, false)
            if (useSimpleSummaryProvider) {
                setSummaryProvider { value.toString() }
            }

            val summaryFormat = getString(R.styleable.CustomPreference_summaryFormat)
            if (summaryFormat != null) {
                setSummaryProvider { String.format(summaryFormat, value) }
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restore, defaultValue)
        mValue = getPersistedInt(default)
        mValue = if (restore) {
            if (shouldPersist()) getPersistedInt(default) else 0
        } else {
            defaultValue as Int
        }
    }

    var value: Int
        get() = if (mValue == 0) {
            getPersistedInt(default)
        } else {
            mValue
        }
        set(value) {
            mValue = value
            persistInt(value)
        }

    private fun onValueUpdated() {
        if (shouldPersist()) {
            persistInt(mValue)
        }
        callChangeListener(mValue)
    }

    private val valueText: String
        get() = mValue.toString() + suffix

    // TODO: These could do with some thought as to either documentation, or defining the coupling between here and
    // SeekBarDialogFragmentCompat
    private fun setRelativeValue(value: Int) {
        mValue = value * interval + min
    }

    private val relativeMax: Int
        get() = (max - min) / interval
    private val relativeProgress: Int
        get() = (mValue - min) / interval

    private fun setupTempValue() {
        if (!shouldPersist()) {
            return
        }
        mValue = getPersistedInt(default)
    }

    class SeekBarDialogFragmentCompat : PreferenceDialogFragmentCompat(), OnSeekBarChangeListener {
        private lateinit var seekLine: LinearLayout
        private lateinit var seekBar: SeekBar
        private lateinit var valueText: TextView

        override fun getPreference(): SeekBarPreferenceCompat {
            return super.getPreference() as SeekBarPreferenceCompat
        }

        override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
            if (fromUser) {
                preference.setRelativeValue(value)
                preference.onValueUpdated()
                onValueUpdated()
            }
        }

        private fun onValueUpdated() {
            valueText.text = preference.valueText
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            // intentionally left blank
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            preference.notifyChanged() // to reload the summary with summaryProvider
            this.dialog!!.dismiss()
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            // nothing needed - see onStopTrackingTouch
        }

        override fun onBindDialogView(v: View) {
            super.onBindDialogView(v)
            seekBar.max = preference.relativeMax
            seekBar.progress = preference.relativeProgress
        }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            builder.setNegativeButton(null, null)
            builder.setPositiveButton(null, null)
            builder.setTitle(null)
        }

        override fun onCreateDialogView(context: Context): View {
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(6, 6, 6, 6)
            valueText = FixedTextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 32f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(valueText, params)

            if (preference.xLabel != 0 && preference.yLabel != 0) {
                val paramsSeekbar = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                paramsSeekbar.setMargins(0, 12, 0, 0)
                seekLine = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(6, 6, 6, 6)
                }
                addLabelsBelowSeekBar(context)
                layout.addView(seekLine, paramsSeekbar)
            }
            preference.setupTempValue()

            seekBar = SeekBar(context).apply {
                setOnSeekBarChangeListener(this@SeekBarDialogFragmentCompat)
                max = preference.relativeMax
                progress = preference.relativeProgress
            }
            layout.addView(
                seekBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            onValueUpdated()
            return layout
        }

        private fun addLabelsBelowSeekBar(context: Context) {
            val labels = intArrayOf(preference.xLabel, preference.yLabel)
            for (count in 0..1) {
                val textView = FixedTextView(context).apply {
                    text = context.getString(labels[count])
                    gravity = Gravity.START
                }
                seekLine.addView(textView)

                textView.layoutParams = if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) {
                    if (count == 1) {
                        getLayoutParams(0.0f)
                    } else {
                        getLayoutParams(1.0f)
                    }
                } else {
                    if (count == 0) {
                        getLayoutParams(0.0f)
                    } else {
                        getLayoutParams(1.0f)
                    }
                }
            }
        }

        fun getLayoutParams(weight: Float): LinearLayout.LayoutParams {
            return LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
            )
        }
    }

    override fun makeDialogFragment() = SeekBarDialogFragmentCompat()
}
