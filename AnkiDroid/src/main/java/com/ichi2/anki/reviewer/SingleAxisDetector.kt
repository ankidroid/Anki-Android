// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import android.view.MotionEvent

class SingleAxisDetector<B : MappableBinding, A : MappableAction<B>>(
    private val action: A,
    val mappableBinding: B,
) {
    private val axis: Axis? = (mappableBinding.binding as? Binding.AxisButtonBinding)?.axis
    private val threshold: Float = (mappableBinding.binding as? Binding.AxisButtonBinding)?.threshold ?: 0F

    /** If the command has been executed and we have not returned lower than the threshold */
    private var sentAction: Boolean = false

    /** If the command executed since we reached the threshold was handled */
    private var isHandled: Boolean = false

    /**
     * Given a [MotionEvent], determine whether we've reached [threshold].
     *
     * If we have, and [action] has not been sent in the period that we reached the threshold
     * then send it once to [processor] and wait for the value to go back under the threshold
     *
     * @return whether [processor] handled [action] in the period that we reached the threshold,
     * so the rest of a handled movement also counts as handled
     */
    fun onGenericMotionEvent(
        ev: MotionEvent,
        processor: BindingProcessor<B, A>?,
    ): Boolean {
        if (!hasReachedThreshold(ev)) {
            sentAction = false
            isHandled = false
        } else if (!sentAction) {
            sentAction = true
            isHandled = processor?.processAction(action, mappableBinding) == true
        }
        return isHandled
    }

    private fun hasReachedThreshold(ev: MotionEvent): Boolean {
        // TODO: We may need to handle historical events as well
        val value = ev.getAxisValue(axis?.motionEventValue ?: MotionEvent.INVALID_POINTER_ID)
        return when {
            threshold > 0 -> value >= threshold
            threshold < 0 -> value <= threshold
            else -> false
        }
    }

    fun toDisplayString(context: Context) = mappableBinding.toDisplayString(context)
}
