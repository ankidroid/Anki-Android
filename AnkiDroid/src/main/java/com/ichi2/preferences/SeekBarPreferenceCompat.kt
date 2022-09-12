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
import androidx.core.os.bundleOf
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.annotations.NeedsTest
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.KotlinCleanup

@NeedsTest("removing JvmOverloads should fail")
class SeekBarPreferenceCompat
@JvmOverloads // fixes: Error inflating class com.ichi2.preferences.SeekBarPreferenceCompat
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.dialogPreferenceStyle,
    defStyleRes: Int = R.style.Preference_DialogPreference
) : DialogPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var mSuffix: String
    private var mDefault = 0
    private var mMax = 0
    private var mMin = 0
    private var mInterval = 0
    private var mValue = 0

    @StringRes
    private var mXLabel = 0

    @StringRes
    private var mYLabel = 0

    init {
        mSuffix = attrs?.getAttributeValue(androidns, "text") ?: ""
        mDefault = attrs?.getAttributeIntValue(androidns, "defaultValue", 0) ?: 0
        mMax = attrs?.getAttributeIntValue(androidns, "max", 100) ?: 100
        mMin = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "min", 0) ?: 0
        mInterval = attrs?.getAttributeIntValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "interval", 1) ?: 1
        mXLabel = attrs?.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "xlabel", 0) ?: 0
        mYLabel = attrs?.getAttributeResourceValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "ylabel", 0) ?: 0

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
        mValue = getPersistedInt(mDefault)
        mValue = if (restore) {
            if (shouldPersist()) getPersistedInt(mDefault) else 0
        } else {
            defaultValue as Int
        }
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

    private fun onValueUpdated() {
        if (shouldPersist()) {
            persistInt(mValue)
        }
        callChangeListener(mValue)
    }

    private val valueText: String
        get() = mValue.toString() + mSuffix

    // TODO: These could do with some thought as to either documentation, or defining the coupling between here and
    // SeekBarDialogFragmentCompat
    private fun setRelativeValue(value: Int) {
        mValue = value * mInterval + mMin
    }

    private val relativeMax: Int
        get() = (mMax - mMin) / mInterval
    private val relativeProgress: Int
        get() = (mValue - mMin) / mInterval

    private fun setupTempValue() {
        if (!shouldPersist()) {
            return
        }
        mValue = getPersistedInt(mDefault)
    }

    class SeekBarDialogFragmentCompat : PreferenceDialogFragmentCompat(), OnSeekBarChangeListener {
        private lateinit var mSeekLine: LinearLayout
        private lateinit var mSeekBar: SeekBar
        private lateinit var mValueText: TextView

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
            mValueText.text = preference.valueText
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
            mSeekBar.max = preference.relativeMax
            mSeekBar.progress = preference.relativeProgress
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
            mValueText = FixedTextView(context).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                textSize = 32f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layout.addView(mValueText, params)
            @KotlinCleanup("maybe use scope function to make mSeekBar available to code below?")
            mSeekBar = SeekBar(context)
            mSeekBar.setOnSeekBarChangeListener(this)
            layout.addView(
                mSeekBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            val preference = preference
            if (preference.mXLabel != 0 && preference.mYLabel != 0) {
                val paramsSeekbar = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                paramsSeekbar.setMargins(0, 12, 0, 0)
                mSeekLine = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(6, 6, 6, 6)
                }
                addLabelsBelowSeekBar(context)
                layout.addView(mSeekLine, paramsSeekbar)
            }
            preference.setupTempValue()
            mSeekBar.max = preference.relativeMax
            mSeekBar.progress = preference.relativeProgress
            onValueUpdated()
            return layout
        }

        private fun addLabelsBelowSeekBar(context: Context) {
            val labels = intArrayOf(preference.mXLabel, preference.mYLabel)
            for (count in 0..1) {
                val textView = FixedTextView(context).apply {
                    text = context.getString(labels[count])
                    gravity = Gravity.START
                }
                mSeekLine.addView(textView)

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
                LinearLayout.LayoutParams.WRAP_CONTENT, weight
            )
        }

        companion object {
            fun newInstance(key: String): SeekBarDialogFragmentCompat {
                return SeekBarDialogFragmentCompat().apply {
                    arguments = bundleOf(ARG_KEY to key)
                }
            }
        }
    }

    companion object {
        @KotlinCleanup(
            "this string should be extracted to a constant class and made public for the entire app"
        )
        private const val androidns = "http://schemas.android.com/apk/res/android"
    }
}
