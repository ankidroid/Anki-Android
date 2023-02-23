/****************************************************************************************
 * Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

package com.ichi2.preferences

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.utils.stringIterable
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber

@Suppress("deprecation") // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
class StepsPreference : android.preference.EditTextPreference, AutoFocusable {
    private val mAllowEmpty: Boolean

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        mAllowEmpty = getAllowEmptyFromAttributes(attrs)
        updateSettings()
    }

    @Suppress("unused")
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        mAllowEmpty = getAllowEmptyFromAttributes(attrs)
        updateSettings()
    }

    @Suppress("unused")
    constructor(context: Context?) : super(context) {
        mAllowEmpty = getAllowEmptyFromAttributes(null)
        updateSettings()
    }

    override fun onBindDialogView(view: View?) {
        super.onBindDialogView(view)
        autoFocusAndMoveCursorToEnd(editText)
    }

    /**
     * Update settings to show a numeric keyboard instead of the default keyboard.
     * <p>
     * This method should only be called once from the constructor.
     */
    private fun updateSettings() {
        // Use the number pad but still allow normal text for spaces and decimals.
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_CLASS_TEXT
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val validated = getValidatedStepsInput(editText.text.toString())
            if (validated == null) {
                showThemedToast(context, context.resources.getString(R.string.steps_error), false)
            } else if (validated.isEmpty() && !mAllowEmpty) {
                showThemedToast(
                    context,
                    context.resources.getString(R.string.steps_min_error),
                    false
                )
            } else {
                text = validated
            }
        }
    }

    /**
     * Check if the string is a valid format for steps and return that string, reformatted for better usability if
     * needed.
     *
     * @param steps User input in text editor.
     * @return The correctly formatted string or null if the input is not valid.
     */
    private fun getValidatedStepsInput(steps: String): String? {
        val stepsAr = convertToJSON(steps)
        return if (stepsAr == null) {
            null
        } else {
            val sb = StringBuilder()
            for (step in stepsAr.stringIterable()) {
                sb.append(step).append(" ")
            }
            sb.toString().trim { it <= ' ' }
        }
    }

    private fun getAllowEmptyFromAttributes(attrs: AttributeSet?): Boolean {
        return attrs?.getAttributeBooleanValue(AnkiDroidApp.XML_CUSTOM_NAMESPACE, "allowEmpty", true)
            ?: true
    }

    companion object {
        /**
         * Convert steps format.
         *
         * @param a JSONArray representation of steps.
         * @return The steps as a space-separated string.
         */
        fun convertFromJSON(a: JSONArray): String {
            val sb = StringBuilder()
            for (s in a.stringIterable()) {
                sb.append(s).append(" ")
            }
            return sb.toString().trim { it <= ' ' }
        }

        /**
         * Convert steps format. For better usability, rounded floats are converted to integers (e.g., 1.0 is converted to
         * 1).
         *
         * @param steps String representation of steps.
         * @return The steps as a JSONArray or null if the steps are not valid.
         */
        fun convertToJSON(steps: String): JSONArray? {
            val stepsAr = JSONArray()
            val stepsTrim = steps.trim { it <= ' ' }
            if (steps.isEmpty()) {
                return stepsAr
            }
            try {
                for (s in stepsTrim.split("\\s+".toRegex()).toTypedArray()) {
                    val d = s.toDouble()
                    // 0 or less is not a valid step.
                    if (d <= 0) {
                        return null
                    }
                    // Use whole numbers if we can (but still allow decimals)
                    val i = d.toInt()
                    if (i.toDouble() == d) {
                        stepsAr.put(i)
                    } else {
                        stepsAr.put(d)
                    }
                }
            } catch (e: NumberFormatException) {
                // Can't serialize float. Value likely too big/small.
                Timber.w(e)
                return null
            } catch (e: JSONException) {
                Timber.w(e)
                return null
            }
            return stepsAr
        }
    }
}
