// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.withPref
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardBrowserBottomNavigationEndTest : RobolectricTest() {
    @Test
    fun `dragging the visible scrollbar to the bottom reaches the last card`() =
        runTest {
            shadowOf(targetContext.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
            withPref(Prefs::devBottomNavEnabled, true) {
                withPref(Prefs::devUsingCardBrowserSearchView, true) {
                    repeat(30) { addBasicNote("Card $it", "Back $it") }
                    setIntroductionSlidesShown(true)
                    val picker = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()
                    val navigation = assertNotNull(picker.binding.bottomNavigation)
                    navigation.selectedItemId = NavigationItem.BROWSER.id
                    advanceRobolectricLooper()
                    val browser = picker.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                    runBlocking { browser.activityViewModel.searchJob?.join() }
                    advanceRobolectricLooper()
                    val appBar = picker.findViewById<AppBarLayout>(R.id.browser_app_bar)
                    appBar.setExpanded(true, false)
                    advanceRobolectricLooper()
                    val list = picker.findViewById<RecyclerView>(R.id.card_browser_list)
                    val scroller = picker.findViewById<RecyclerFastScroller>(R.id.browser_scroller)
                    scroller.isHidingEnabled = false
                    scroller.show(false)
                    advanceRobolectricLooper()
                    assertTrue(scroller.handle.isEnabled, "The scrollbar must accept a drag")
                    val time = SystemClock.uptimeMillis()
                    val y = (navigation.topInWindow - scroller.topInWindow - scroller.handle.height / 2).toFloat() - 1
                    assertTrue(y > 0 && scroller.topInWindow + y < navigation.topInWindow, "The touch must stay above navigation")
                    val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 1f, y, 0)
                    try {
                        assertTrue(scroller.onTouchEvent(event))
                    } finally {
                        event.recycle()
                    }
                    advanceRobolectricLooper()
                    assertFalse(list.canScrollVertically(1), "Dragging the visible fast scroller to the bottom must reach the end")
                    assertTrue(HideViewOnScrollBehavior.from(navigation).isScrolledIn)
                    assertTrue(appBar.top == 0, "The toolbar must remain expanded")
                    val lastPosition = assertNotNull(list.adapter).itemCount - 1
                    val last = assertNotNull(list.findViewHolderForAdapterPosition(lastPosition)).itemView
                    assertTrue(
                        last.topInWindow + last.height <= navigation.topInWindow,
                        "Last card bottom=${last.topInWindow + last.height}, navigation=${navigation.topInWindow}, " +
                            "list bottom=${list.topInWindow + list.height}, padding=${list.paddingBottom}",
                    )
                }
            }
        }

    private val View.topInWindow: Int
        get() = IntArray(2).also(::getLocationInWindow)[1]
}
