// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.widget.Toolbar
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.databinding.ViewSyncProgressLayoutBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25, 36])
class SyncActionProviderTest : RobolectricTest() {
    @Test
    fun `long press runs the action after the tooltip is set`() {
        val controller = Robolectric.buildActivity(AnkiActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val item = Toolbar(activity).menu.add("Sync")
        val provider = SyncActionProvider(activity)
        val button = ViewSyncProgressLayoutBinding.bind(provider.onCreateActionView(item)).button
        var longPressed = false
        provider.setLongClickAction("Media sync") { longPressed = true }
        provider.setTooltipText("Sync")

        button.performLongClick()

        assertTrue(longPressed, "the long press action should run")
    }
}
