// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitForPageCondition
import com.ichi2.anki.testutil.waitUntil
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Uses the real Statistics document: Robolectric does not execute its focus/scroll JavaScript. */
@RunWith(AndroidJUnit4::class)
class StatisticsLastGraphClearanceTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun lastStatisticsGraphClearsVisibleNavigationWithExpandedToolbar() = verifyLastGraphClearance()

    @Test
    fun zoomedStatisticsGraphRemainsVisibleAtEnd() = verifyLastGraphClearance(zoomed = true)

    private fun verifyLastGraphClearance(zoomed: Boolean = false) {
        assumeTrue(testContext.resources.getBoolean(R.bool.bottom_navigation_available))
        val preferences = testContext.sharedPrefs()
        val navigationKey = testContext.getString(R.string.dev_bottom_nav_key)
        val savedPreferences =
            listOf(navigationKey, "backupPromptDisabled").associateWith { key ->
                if (preferences.contains(key)) preferences.getBoolean(key, false) else null
            }
        disableIntroductionSlide()
        preferences.edit(commit = true) {
            putBoolean(navigationKey, true)
            putBoolean("backupPromptDisabled", true)
        }
        try {
            addNoteUsingBasicNoteType()
            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                if (zoomed) {
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    waitUntil(message = { "Statistics did not rotate to landscape" }) {
                        var landscape = false
                        scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                        landscape
                    }
                }
                scenario.onActivity {
                    it
                        .findViewById<BottomNavigationView>(
                            R.id.bottom_navigation,
                        ).selectedItemId = NavigationItem.STATS.id
                }
                lateinit var statistics: Statistics
                lateinit var navigation: BottomNavigationView
                lateinit var webView: WebView
                waitUntil(message = { "Statistics did not attach its WebView" }) {
                    var ready = false
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager.findFragmentByTag(NavigationItem.STATS.tag) as? Statistics
                        if (fragment?.view != null) {
                            statistics = fragment
                            navigation = activity.findViewById(R.id.bottom_navigation)
                            webView = statistics.webViewLayout.getChildAt(0) as WebView
                            ready = true
                        }
                    }
                    ready
                }
                statistics.waitForPageCondition(
                    "document.querySelectorAll('input[type=radio]').length > 8 && document.body.style.paddingBottom !== ''",
                    "Statistics did not render its controls and bottom inset",
                )
                scenario.onActivity {
                    // Retain the shown navigation and expanded toolbar while accessibility-style
                    // document scrolling moves to the real end of the graph list.
                    webView.setOnScrollChangeListener(null)
                    HideViewOnScrollBehavior.from(navigation).slideIn(navigation, false)
                }
                if (zoomed) {
                    scenario.onActivity { webView.zoomBy(3f) }
                    webView.awaitJavascript("visualViewport.scale > 2.5")
                    scenario.onActivity { webView.pageDown(true) }
                    webView.awaitJavascript("visualViewport.pageTop + visualViewport.height >= document.scrollingElement.scrollHeight - 2")
                } else {
                    webView.evaluate("scrollTo(0, document.scrollingElement.scrollHeight)")
                    webView.awaitJavascript("scrollY + innerHeight >= document.scrollingElement.scrollHeight - 2")
                }
                var safeBottomCssPixels = 0f
                scenario.onActivity {
                    safeBottomCssPixels = (navigation.yInWindow - webView.yInWindow) / statistics.webViewLayout.scale
                    assertTrue(HideViewOnScrollBehavior.from(navigation).isScrolledIn)
                }
                val geometry =
                    JSONObject(
                        webView.evaluate(
                            "({bottom: document.querySelector('.graphs-container').getBoundingClientRect().bottom, offsetTop: window.visualViewport?.offsetTop || 0})",
                        ),
                    )
                val graphBottom = geometry.getDouble("bottom") - geometry.getDouble("offsetTop")
                if (zoomed) {
                    assertTrue(graphBottom > 0, "The final graph must remain on screen at the end of the zoomed page: $geometry")
                } else {
                    assertTrue(
                        graphBottom <= safeBottomCssPixels + 1,
                        "Last Statistics graph ends at $graphBottom, below navigation at $safeBottomCssPixels CSS pixels",
                    )
                }
            }
        } finally {
            preferences.edit(commit = true) {
                savedPreferences.forEach { (key, value) ->
                    if (value == null) remove(key) else putBoolean(key, value)
                }
            }
        }
    }

    private val View.yInWindow: Int
        get() = IntArray(2).also(::getLocationInWindow)[1]
}
