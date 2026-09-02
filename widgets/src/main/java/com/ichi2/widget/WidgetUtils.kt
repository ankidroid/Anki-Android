// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * @return An [AppWidgetManager] for the provided context, or `null`
 *
 * @see AppWidgetManager.getInstance
 */

fun getAppWidgetManager(context: Context): AppWidgetManager? {
    // The call returns null on a Supernote A5X, but as the underlying platform call is in Java,
    // the result is assumed to be non-null in Kotlin
    return AppWidgetManager.getInstance(context)
}

/** Whether 'Material You' dynamic color should be used for widgets */
val disableMaterialYouDynamicColor: Boolean
    get() = true
