//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import android.content.Context
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.annotation.StringRes
import com.ichi2.libanki.utils.Time
import java.util.*

object UIUtils {
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
    ): Float {
        return context.resources.displayMetrics.density * value
    }

    fun getDayStart(time: Time): Long {
        val cal = time.calendar()
        if (cal[Calendar.HOUR_OF_DAY] < 4) {
            cal.roll(Calendar.DAY_OF_YEAR, -1)
        }
        cal[Calendar.HOUR_OF_DAY] = 4
        cal[Calendar.MINUTE] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 0
        return cal.timeInMillis
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit.
     * @param context Context to get resources and device specific display metrics.
     * @return A float value to represent px value which is equivalent to the passed dp value.
     */
    fun convertDpToPixel(
        dp: Float,
        context: Context,
    ): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}
