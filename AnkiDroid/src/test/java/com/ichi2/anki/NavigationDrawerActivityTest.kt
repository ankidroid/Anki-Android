// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.widget.WidgetPermissionReceiver
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class NavigationDrawerActivityTest : RobolectricTest() {
    @Test
    fun `widget update broadcast resolves to WidgetPermissionReceiver`() =
        deckPicker {
            val broadcast = shadowOf(this).broadcastIntents.last { it.action == "com.ichi2.widget.UPDATE_WIDGET" }

            @Suppress("DEPRECATION")
            val receivers = packageManager.queryBroadcastReceivers(broadcast, 0)

            assertEquals(listOf(WidgetPermissionReceiver::class.java.name), receivers.map { it.activityInfo.name })
        }
}
