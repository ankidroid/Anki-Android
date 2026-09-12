// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Context
import android.widget.ImageView
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.utils.ext.setImageDrawableSafe

/**
 * RecyclerView uses `activityHasBackground` to skip per-row colors. Clearing the drawable on
 * stop must not flip that flag, which would notify the list adapter while stopped.
 *
 * @param drawableApplied `true`/`false` after a resolve attempt; `null` when clearing on stop.
 */
fun nextActivityHasBackground(
    current: Boolean,
    drawableApplied: Boolean?,
): Boolean = drawableApplied ?: current

/**
 * Failure/too-large toasts for the DeckPicker background. Shown at most once per activity instance
 * so returning from the reviewer does not re-toast the same problem.
 */
class BackgroundFailureToastState {
    private var toasted = false

    fun shouldToast(): Boolean {
        if (toasted) return false
        toasted = true
        return true
    }
}

/**
 * Applies a [BackgroundImage.ResolveResult] (or `null` to clear on stop) to [imageView].
 *
 * @return the next `activityHasBackground` value
 */
fun applyLoadedDeckPickerBackground(
    context: Context,
    imageView: ImageView,
    result: BackgroundImage.ResolveResult?,
    toastState: BackgroundFailureToastState,
    currentHasBackground: Boolean,
): Boolean {
    if (result == null) {
        imageView.setImageDrawableSafe(null)
        return nextActivityHasBackground(currentHasBackground, drawableApplied = null)
    }
    if (result is BackgroundImage.ResolveResult.Failure && toastState.shouldToast()) {
        showThemedToast(context, result.message(context), shortLength = false)
    }
    val drawable = (result as? BackgroundImage.ResolveResult.Ready)?.drawable
    val applied =
        imageView.setImageDrawableSafe(drawable) {
            if (toastState.shouldToast()) {
                showThemedToast(context, context.getString(R.string.background_image_too_large), shortLength = false)
            }
        }
    return nextActivityHasBackground(
        current = currentHasBackground,
        drawableApplied = applied && drawable != null,
    )
}
