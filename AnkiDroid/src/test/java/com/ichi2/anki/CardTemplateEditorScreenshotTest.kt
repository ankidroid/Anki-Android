// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.core.app.ActivityScenario
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [CardTemplateEditor]
 *
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CardTemplateEditorScreenshotTest"`
 */
class CardTemplateEditorScreenshotTest : ScreenshotTest() {
    @Test
    fun basic() {
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy("Basic")
        val intent = CardTemplateEditor.getIntent(targetContext, collectionBasicNoteTypeOriginal.id)

        ActivityScenario.launch<CardTemplateEditor>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("basic")
            }
        }
    }

    /**
     * Landscape with 3-button navigation showing the second card.
     */
    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withCardTemplateEditor(noteType = getCurrentDatabaseNoteTypeCopy("Basic (and reversed card)")) {
            mainBinding.cardTemplateEditorPager.setCurrentItem(1, false)
            advanceRobolectricLooper()
            simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    @SuppressLint("RtlHardcoded") // insets and cutouts are physical: not layout-direction relative
    private fun CardTemplateEditor.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val cutoutWidth = 32.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(left = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(right = cutoutWidth))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        advanceRobolectricLooper()
        addOverlay(navBarWidth, Gravity.LEFT)
        addOverlay(cutoutWidth, Gravity.RIGHT)
    }

    private fun CardTemplateEditor.addOverlay(
        width: Dp,
        gravity: Int,
    ) {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, gravity))
    }
}
