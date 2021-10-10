//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.ichi2.async.CollectionTask.SaveCollection
import com.ichi2.async.TaskListener
import com.ichi2.async.TaskManager
import com.ichi2.libanki.utils.Time
import timber.log.Timber
import java.util.*
import kotlin.jvm.JvmOverloads

object UIUtils {
    @JvmStatic
    fun showThemedToast(context: Context?, text: String?, shortLength: Boolean) {
        Toast.makeText(context, text, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    @JvmStatic
    fun showThemedToast(context: Context?, text: CharSequence?, shortLength: Boolean) {
        showThemedToast(context, text.toString(), shortLength)
    }

    @JvmStatic
    fun showThemedToast(context: Context?, @StringRes textResource: Int, shortLength: Boolean) {
        Toast.makeText(context, textResource, if (shortLength) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    /**
     * Show a simple Toast-like Snackbar with no actions.
     * To enable swipe-to-dismiss, the Activity layout should include a CoordinatorLayout with id "root_layout"
     */
    @JvmStatic
    fun showSimpleSnackbar(activity: Activity, mainTextResource: Int, shortLength: Boolean): Snackbar? {
        val root = activity.findViewById<View>(R.id.root_layout)
        return showSnackbar(activity, mainTextResource, shortLength, -1, null, root)
    }

    @JvmStatic
    fun showSimpleSnackbar(activity: Activity, mainText: String?, shortLength: Boolean): Snackbar? {
        val root = activity.findViewById<View>(R.id.root_layout)
        return showSnackbar(activity, mainText, shortLength, -1, null, root, null)
    }

    /**
     * Show a snackbar with an action
     * @param mainTextResource resource for the main text string
     * @param shortLength whether or not to use long length
     * @param actionTextResource resource for the text string shown as the action
     * @param listener listener for the action (if null no action shown)
     * @param root View Snackbar will attach to. Should be CoordinatorLayout for swipe-to-dismiss to work.
     * @return Snackbar object
     */
    @JvmStatic
    @JvmOverloads
    fun showSnackbar(
        activity: Activity,
        mainTextResource: Int,
        shortLength: Boolean,
        actionTextResource: Int,
        listener: View.OnClickListener?,
        root: View?,
        callback: Snackbar.Callback? = null
    ): Snackbar? {
        val mainText = activity.resources.getString(mainTextResource)
        return showSnackbar(activity, mainText, shortLength, actionTextResource, listener, root, callback)
    }

    @JvmStatic
    fun showSnackbar(
        activity: Activity,
        mainText: String?,
        shortLength: Boolean,
        actionTextResource: Int,
        listener: View.OnClickListener?,
        root: View?,
        callback: Snackbar.Callback?
    ): Snackbar? {
        return showSnackbar(activity, mainText, if (shortLength) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG, actionTextResource, listener, root, callback)
    }

    @JvmStatic
    fun showSnackbar(
        activity: Activity,
        mainText: String?,
        length: Int,
        actionTextResource: Int,
        listener: View.OnClickListener?,
        rootView: View?,
        callback: Snackbar.Callback?
    ): Snackbar? {
        var root = rootView
        if (root == null) {
            root = activity.findViewById(android.R.id.content)
            if (root == null) {
                Timber.e("Could not show Snackbar due to null View")
                return null
            }
        }
        val sb = getSnackbar(activity, mainText, length, actionTextResource, listener, root, callback)
        sb.show()
        return sb
    }

    @JvmStatic
    fun getSnackbar(activity: Activity?, mainText: String?, length: Int, actionTextResource: Int, listener: View.OnClickListener?, root: View, callback: Snackbar.Callback?): Snackbar {
        val sb = Snackbar.make(root, mainText!!, length)
        if (listener != null) {
            sb.setAction(actionTextResource, listener)
        }
        if (callback != null) {
            sb.addCallback(callback)
        }
        // Make the text white to avoid interference from our theme colors.
        val view = sb.view
        val tv = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        val action = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        if (tv != null && action != null) {
            tv.setTextColor(Color.WHITE)
            action.setTextColor(ContextCompat.getColor(activity!!, R.color.material_light_blue_500))
            tv.maxLines = 2 // prevent tablets from truncating to 1 line
        }
        return sb
    }

    @JvmStatic
    fun getDensityAdjustedValue(context: Context, value: Float): Float {
        return context.resources.displayMetrics.density * value
    }

    @JvmStatic
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

    @JvmStatic
    @JvmOverloads
    fun saveCollectionInBackground(syncIgnoresDatabaseModification: Boolean = false) {
        if (CollectionHelper.getInstance().colIsOpen()) {
            val listener: TaskListener<Void?, Void?> = object : TaskListener<Void?, Void?>() {
                override fun onPreExecute() {
                    Timber.d("saveCollectionInBackground: start")
                }

                override fun onPostExecute(v: Void?) {
                    Timber.d("saveCollectionInBackground: finished")
                }
            }
            TaskManager.launchCollectionTask(SaveCollection(syncIgnoresDatabaseModification), listener)
        }
    }

    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit.
     * @param context Context to get resources and device specific display metrics.
     * @return A float value to represent px value which is equivalent to the passed dp value.
     */
    @JvmStatic
    fun convertDpToPixel(dp: Float, context: Context): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}
