//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun showThemedToast(
    context: Context,
    text: String,
    shortLength: Boolean,
) {
    Toast.makeText(context, text, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

fun showThemedToast(
    context: Context,
    text: CharSequence,
    shortLength: Boolean,
) {
    showThemedToast(context, text.toString(), shortLength)
}

fun showThemedToast(
    context: Context,
    @StringRes textResource: Int,
    shortLength: Boolean,
) {
    Toast.makeText(context, textResource, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

fun getDensityAdjustedValue(
    context: Context,
    value: Float,
): Float = context.resources.displayMetrics.density * value
