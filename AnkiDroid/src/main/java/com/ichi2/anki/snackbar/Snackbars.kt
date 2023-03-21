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
import android.os.Build
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.onAttachedToWindow2
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
    snackbar.setMaxLines(4)
    snackbar.behavior = SwipeDismissBehaviorFix()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        snackbar.fixMarginsWhenInsetsChange()
    }

    if (snackbarBuilder != null) { snackbar.snackbarBuilder() }

    snackbar.show()
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
fun Fragment.showSnackbar(
    text: CharSequence,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    requireActivity().showSnackbar(text, duration, snackbarBuilder)
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
fun Fragment.showSnackbar(
    @StringRes textResource: Int,
    duration: Int = Snackbar.LENGTH_LONG,
    snackbarBuilder: SnackbarBuilder? = null
) {
    val text = resources.getText(textResource)
    showSnackbar(text, duration, snackbarBuilder)
}

/* ********************************************************************************************** */

fun Snackbar.setMaxLines(maxLines: Int) {
    view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = maxLines
}

/**
 * When bottom inset change, for instance, when keyboard is open or closed,
 * snackbar fails to adjust its margins and can appear too high or too low.
 * While snackbar does employ an `OnApplyWindowInsetsListener`, its methods don't get called.
 * This here is an atrocious workaround that solves the issue. It is awful and despicable.
 *
 * First of all, we use our own `OnApplyWindowInsetsListener`. Note that we *need to post it*,
 * as apparently something else resets it after this call. (Not sure if it's feasible
 * to find out what as this requires method breakpoints, which are prohibitively slow.)
 * Also, if we set an inset listener for a view, [View.dispatchApplyWindowInsets] will call
 * `onApplyWindowInsets` on our listener rather than the view, so we better call the original.
 *
 * Then, we want to call [Snackbar.updateMargins], which is private,
 * and the one method is not private that calls it is [Snackbar.onAttachedToWindow],
 * so we hack into its package namespace using a helper method.
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun Snackbar.fixMarginsWhenInsetsChange() {
    view.post {
        view.rootView.setOnApplyWindowInsetsListener { rootView, insets ->
            onAttachedToWindow2()
            rootView.onApplyWindowInsets(insets)
        }
    }

    addCallback(object : Snackbar.Callback() {
        override fun onDismissed(snackbar: Snackbar, event: Int) {
            view.rootView.setOnApplyWindowInsetsListener(null)
        }
    })
}
