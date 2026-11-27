// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h500dp")
class MoreRotaryScrollTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    @SuppressLint("InlinedApi")
    fun `a rotary controller scrolls the More list`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            setIntroductionSlidesShown(true)
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            checkNotNull(activity.binding.bottomNavigation).selectedItemId = R.id.nav_more
            advanceRobolectricLooper()
            val settings = composeRule.onNodeWithText(activity.getString(CommonString.settings))
            settings.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            settings.assertIsFocused()

            val scroll =
                (
                    activity.supportFragmentManager.findFragmentByTag(
                        BottomNavController.NavigationItem.MORE.tag,
                    ) as MoreFragment
                ).scrollState
            val time = SystemClock.uptimeMillis()
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, -3f) }
            val event =
                MotionEvent.obtain(
                    time,
                    time,
                    MotionEvent.ACTION_SCROLL,
                    1,
                    arrayOf(properties),
                    arrayOf(coordinates),
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    InputDevice.SOURCE_ROTARY_ENCODER,
                    0,
                )
            try {
                activity.dispatchGenericMotionEvent(event)
                composeRule.waitForIdle()
                advanceRobolectricLooper()
                assertTrue(scroll.value > 0, "Rotary input must scroll More")
            } finally {
                event.recycle()
            }
        }
}
