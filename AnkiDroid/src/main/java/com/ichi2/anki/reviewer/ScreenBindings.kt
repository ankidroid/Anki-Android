// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.android.AnkiShakeDetector
import com.ichi2.anki.cardviewer.Gesture
import com.squareup.seismic.ShakeDetector
import timber.log.Timber

/**
 * Triggers the bindings of the screen of [fragment] from its key presses, joystick movements,
 * gestures and shakes.
 *
 * [bindingMaps] are in priority order: an input only reaches a map if the previous ones didn't handle it.
 *
 * Must be created once the view of [fragment] is created: joystick movements go to the focused view,
 * so they are taken from the whole view, and shakes are taken while the view is started.
 */
class ScreenBindings(
    private val fragment: Fragment,
    private vararg val bindingMaps: BindingMap<*, *>,
) : ShakeDetector.Listener {
    init {
        fragment.requireView().setOnGenericMotionListener { _, event ->
            bindingMaps.any { it.onGenericMotionEvent(event) }
        }
        if (isBound(Gesture.SHAKE)) {
            AnkiShakeDetector.createInstance(fragment.requireContext(), this)?.let { shakeDetector ->
                fragment.viewLifecycleOwner.lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) = shakeDetector.start()

                        override fun onStop(owner: LifecycleOwner) = shakeDetector.stop()
                    },
                )
            }
        }
    }

    /** Triggers the bindings of key presses. Their repeats and releases aren't handled */
    fun dispatchKeyEvent(event: KeyEvent): Boolean = event.action == KeyEvent.ACTION_DOWN && bindingMaps.any { it.onKeyDown(event) }

    fun onGesture(gesture: Gesture): Boolean = bindingMaps.any { it.onGesture(gesture) }

    fun isBound(gesture: Gesture): Boolean = bindingMaps.any { it.isBound(gesture) }

    override fun hearShake() {
        // Sensor events still arrive while a dialog or another window covers the screen.
        if (fragment.view?.hasWindowFocus() != true) {
            Timber.d("Ignoring shake: window does not have focus")
            return
        }
        onGesture(Gesture.SHAKE)
    }
}
