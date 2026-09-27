// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Bundle
import android.os.Parcel
import org.robolectric.android.controller.ActivityController

/** Stops the activity and captures its saved instance state. */
fun ActivityController<*>.saveState(): Bundle = Bundle().also { pause().stop().saveInstanceState(it) }

fun Bundle.parcelSize(): Int = withParcel { it.dataSize() }

fun Bundle.parcelledCopy(classLoader: ClassLoader?): Bundle =
    withParcel {
        it.setDataPosition(0)
        requireNotNull(it.readBundle(classLoader))
    }

private fun <T> Bundle.withParcel(action: (Parcel) -> T): T {
    val parcel = Parcel.obtain()
    try {
        parcel.writeBundle(this)
        return action(parcel)
    } finally {
        parcel.recycle()
    }
}
