// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.captionBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h600dp")
class MoreCaptionInsetsTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `the More title clears the window caption bar`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            setIntroductionSlidesShown(true)
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val captionHeight = 48.dp.toPx(activity)
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(captionBar(), Insets.of(0, captionHeight, 0, 0))
                    .setVisible(captionBar(), true)
                    .build()
            ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets)
            advanceRobolectricLooper()

            val title = composeRule.onNodeWithText(activity.getString(CommonString.bottom_nav_more)).fetchSemanticsNode()
            assertTrue(title.positionInWindow.y >= captionHeight, "More title overlaps the caption inset")
        }
}
