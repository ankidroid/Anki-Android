/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.snackbar

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.SwipeDismissBehavior
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import timber.log.Timber

typealias SnackbarBuilder = Snackbar.() -> Unit

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver An [Activity] that has a [CoordinatorLayout] with id `root_layout`.
 * @param textResource String resource to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
@JvmOverloads
fun Activity.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    val text = getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver An [Activity] that has a [CoordinatorLayout] with id `root_layout`.
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun Activity.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    val view: View? = findViewById(R.id.root_layout)

    if (view != null) {
        view.showSnackbar(text, duration, snackbarBuilder)
    } else {
        val errorMessage = "While trying to show a snackbar, " +
            "could not find a view with id root_layout in $this"

        if (BuildConfig.DEBUG) {
            throw IllegalArgumentException(errorMessage)
        } else {
            Timber.e(errorMessage)
            showThemedToast(this, text, false)
        }
    }
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param textResource String resource to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun View.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    val text = resources.getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

/**
 * Show a snackbar.
 *
 * You can create snackbars by calling `showSnackbar` on either an activity or a view.
 * As `CoordinatorLayout` is responsible for proper placement and animation of snackbars,
 *
 *   * if calling on an activity, the activity **MUST** have a `CoordinatorLayout`
 *     with id `root_layout`;
 *
 *   * if calling on a view, the view **MUST** be either a `CoordinatorLayout`,
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 *
 * Any additional configuration can be done in the configuration block, e.g.
 *
 *     showSnackbar(text) {
 *         addCallback(callback)
 *     }
 *
 * @receiver A [View] that is either a [CoordinatorLayout],
 *     or a (possibly indirect) child of `CoordinatorLayout`.
 * @param text Text to show, can be formatted.
 * @param duration Optional. For how long to show the snackbar. Can be one of:
 *     [Snackbar.LENGTH_SHORT], [Snackbar.LENGTH_LONG] (default), [Snackbar.LENGTH_INDEFINITE],
 *     or exact duration in milliseconds.
 * @param snackbarBuilder Optional. A configuration block with the [Snackbar] as `this`.
 */
fun View.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    val snackbar = Snackbar.make(this, text, duration)
    snackbar.setMaxLines(2)
    snackbar.fixSwipeDismissBehavior()

    if (snackbarBuilder != null) { snackbar.snackbarBuilder() }

    snackbar.show()
}

/* ********************************************************************************************** */

fun Snackbar.setMaxLines(maxLines: Int) {
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = maxLines
}

/**
 * This changes the default behavior to the fixed one, preserving the original listener.
 * When dragging or settling, this listener pauses the timer that removes the snackbar,
 * so it does not disappear from under your finger.
 */
private fun Snackbar.fixSwipeDismissBehavior() {
    addCallback(object : Snackbar.Callback() {
        override fun onShown(snackbar: Snackbar) {
            actualBehavior = SwipeDismissBehaviorFix<View>().apply {
                listener = actualBehavior?.listener
            }
        }
    })
}

private var Snackbar.actualBehavior: SwipeDismissBehavior<View>?
    get() {
        return (view.layoutParams as? CoordinatorLayout.LayoutParams)
            ?.behavior as? SwipeDismissBehavior
    }
    set(value) {
        (view.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior = value
    }
