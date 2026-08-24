// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/**
 * Methods for interacting with Robolectric
 */
object Robolectric {
    /**
     * Allows a test activity to be launched under Robolectric
     * This is unusually difficult due to AGP not merging test manifests
     */
    inline fun <reified TestActivity : Activity> registerTestActivity() {
        // https://github.com/robolectric/robolectric/pull/4736
        val context: Context = ApplicationProvider.getApplicationContext()
        val activityInfo =
            ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = context.packageName
            }
        shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
    }
}
