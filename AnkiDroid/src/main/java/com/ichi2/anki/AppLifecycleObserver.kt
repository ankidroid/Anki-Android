// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ichi2.widget.WidgetStatus
import timber.log.Timber

class AppLifecycleObserver(
    private val context: Context,
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED && CollectionManager.isOpenUnsafe()) {
            try {
                WidgetStatus.updateInBackground(context)
            } catch (e: Exception) {
                Timber.w(e)
            }
        }
    }
}
