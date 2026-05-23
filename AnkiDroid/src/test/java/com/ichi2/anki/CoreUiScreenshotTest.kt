// SPDX-FileCopyrightText: 2026 AnkiDroid UI redesign
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.core.content.edit
import androidx.core.view.GravityCompat
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.Before
import org.junit.Test

/**
 * Screenshot tests for core UI surfaces updated in the Anki Design System refresh.
 */
class CoreUiScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @Test
    fun deckPicker_andNavigationDrawer() {
        val intent = DeckPicker.getIntent(targetContext)
        val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
        captureScreen("deckPicker")

        activity.drawerLayout.openDrawer(GravityCompat.START)
        captureScreen("navigationDrawer")
    }
}
