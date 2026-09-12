// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.ui.internationalization

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import com.ichi2.anki.ui.internationalization.BackendTranslations as BT
import com.ichi2.anki.ui.internationalization.PreviewTranslations as PT

@RunWith(AndroidJUnit4::class)
class ComposableTranslationsTest : RobolectricTest() {
    @Test
    fun `defined strings match backend strings`() {
        // BT == BackendTranslations / PT == PreviewTranslations
        assertEquals(BT.actionsAdd(), PT.actionsAdd())
        assertEquals(BT.actionsAddNotetype(), PT.actionsAddNotetype())
        assertEquals(BT.decksStudy(), PT.decksStudy())
        assertEquals(BT.qtMiscBrowse(), PT.qtMiscBrowse())
        assertEquals(
            BT.cardStatsCurrentCard(BT.decksStudy()),
            PT.cardStatsCurrentCard(PT.decksStudy()),
        )
        assertEquals(
            BT.cardStatsCurrentCard(BT.qtMiscBrowse()),
            PT.cardStatsCurrentCard(PT.qtMiscBrowse()),
        )
        assertEquals(
            BT.cardStatsPreviousCard(BT.decksStudy()),
            PT.cardStatsPreviousCard(PT.decksStudy()),
        )
    }
}
