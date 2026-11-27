// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.drawToBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoreBlackThemeFocusTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    @SuppressLint("UseKtx")
    fun `keyboard focus visibly highlights a More row in the Black theme`() =
        withPref(Prefs::appTheme, AppTheme.NIGHT) {
            withPref(Prefs::nightTheme, NightTheme.BLACK) {
                withPref(Prefs::devBottomNavEnabled, true) {
                    InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
                    setIntroductionSlidesShown(true)
                    val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
                    assertEquals(Color.BLACK, MaterialColors.getColor(deckPicker, android.R.attr.colorBackground, Color.TRANSPARENT))

                    val bottomNav = checkNotNull(deckPicker.binding.bottomNavigation)
                    bottomNav.selectedItemId = R.id.nav_more
                    advanceRobolectricLooper()

                    val more =
                        deckPicker.supportFragmentManager.findFragmentByTag(
                            BottomNavController.NavigationItem.MORE.tag,
                        ) as MoreFragment
                    val settings = composeRule.onNodeWithText(deckPicker.getString(CommonString.settings))
                    val help = composeRule.onNodeWithText(deckPicker.getString(CommonString.help_title_using_ankidroid))
                    help.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                    settings.assertIsNotFocused()
                    composeRule.waitForIdle()
                    val bounds = settings.fetchSemanticsNode().boundsInRoot
                    val sampleX = (bounds.right - bounds.width / 8).toInt()
                    val sampleY = bounds.center.y.toInt()
                    val view = more.requireView()
                    val before = view.drawToBitmap().getPixel(sampleX, sampleY)

                    settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                    settings.assertIsFocused()
                    composeRule.waitForIdle()
                    advanceRobolectricLooper()
                    val after = view.drawToBitmap().getPixel(sampleX, sampleY)
                    val brightnessChange =
                        abs(Color.red(after) - Color.red(before)) +
                            abs(Color.green(after) - Color.green(before)) + abs(Color.blue(after) - Color.blue(before))
                    assertTrue(
                        brightnessChange >= 36,
                        "The row highlight should be visible. Before: ${before.toUInt().toString(
                            16,
                        )}, after: ${after.toUInt().toString(16)}",
                    )
                }
            }
        }
}
