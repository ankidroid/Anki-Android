// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ShadowNullExternalFilesDir
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowNullExternalFilesDir::class])
class ManageSpaceNoExternalFilesDirTest : RobolectricTest() {
    @Test
    fun `opening without an external files dir redirects to the app start`() {
        val controller = Robolectric.buildActivity(ManageSpaceActivity::class.java, Intent())
        saveControllerForCleanup(controller)

        val activity = controller.create().get()

        assertThat("Manage space should close", activity.isFinishing, equalTo(true))
        assertThat(shadowOf(activity).nextStartedActivity.component?.className, equalTo(IntentHandler::class.java.name))
    }
}
