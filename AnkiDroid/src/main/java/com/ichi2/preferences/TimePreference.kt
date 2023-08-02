//noinspection MissingCopyrightHeader #8659
package com.ichi2.preferences

import android.R
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TimePicker

@Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
class TimePreference(context: Context?, attrs: AttributeSet?) : android.preference.DialogPreference(context, attrs) {
    private lateinit var mTimepicker: TimePicker
    private var mHours = 0
    private var mMinutes = 0
    override fun onCreateDialogView(): View {
        mTimepicker = TimePicker(context)
        mTimepicker.setIs24HourView(true)
        return mTimepicker
    }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        val time: String = if (restorePersistedValue) {
            if (null == defaultValue) {
                getPersistedString(DEFAULT_VALUE)
            } else {
                getPersistedString(defaultValue.toString())
            }
        } else {
            defaultValue.toString()
        }
        mHours = parseHours(time)
        mMinutes = parseMinutes(time)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        mTimepicker.hour = mHours
        mTimepicker.minute = mMinutes
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            mHours = mTimepicker.hour
            mMinutes = mTimepicker.minute
            val time = String.format("%1$02d:%2$02d", mHours, mMinutes)
            if (callChangeListener(time)) {
                persistString(time)
            }
        }
    }

    companion object {
        const val DEFAULT_VALUE = "00:00"
        fun parseHours(time: String): Int {
            return time.split(":".toRegex()).toTypedArray()[0].toInt()
        }

        fun parseMinutes(time: String): Int {
            return time.split(":".toRegex()).toTypedArray()[1].toInt()
        }
    }

    init {
        setPositiveButtonText(R.string.ok)
        setNegativeButtonText(R.string.cancel)
    }
}
