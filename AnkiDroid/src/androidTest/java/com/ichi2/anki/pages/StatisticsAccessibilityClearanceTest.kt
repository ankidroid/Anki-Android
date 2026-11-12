// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class StatisticsAccessibilityClearanceTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun accessibilityFocusedControlClearsNavigationInLandscape() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags =
                    flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
        try {
            waitUntil(message = { "Touch exploration did not become active" }) {
                testContext.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled
            }
            verifyFocusedStatisticsControl()
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun verifyFocusedStatisticsControl() {
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
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                waitUntil(message = { "Landscape configuration did not arrive" }) {
                    var landscape = false
                    scenario.onActivity {
                        landscape =
                            it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    }
                    landscape
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
                    webView.setOnScrollChangeListener(null)
                    webView.requestFocus()
                }
                webView.awaitJavascript("document.hasFocus()")
                var targetClearance = 0f
                scenario.onActivity { targetClearance = navigation.height / statistics.webViewLayout.scale }
                // Put a real graph control inside the portion of the WebView covered by navigation.
                // It is fully visible to Chromium, so ordinary focus scrolling currently leaves it there.
                assertTrue(
                    webView.evaluate(
                        """
                        (() => {
                            const target = Array.from(document.querySelectorAll('input[type=radio]'))
                                .find(input => input.getBoundingClientRect().top > window.innerHeight * 2);
                            if (!target) return false;
                            target.id = 'statistics-bottom-nav-focus-target';
                            target.setAttribute('aria-label', 'Statistics accessibility target');
                            document.scrollingElement.scrollTop += target.getBoundingClientRect().top
                                - (window.innerHeight - $targetClearance);
                            return true;
                        })();
                        """.trimIndent(),
                    ) == "true",
                    "A graph control below the first viewport must exist",
                )
                // WebView applies programmatic scrolling asynchronously. Wait until the target is
                // fully within its viewport and behind the bar before asking Chromium to focus it.
                webView.awaitJavascript(
                    """
                    (() => {
                        const bounds = document.getElementById('statistics-bottom-nav-focus-target').getBoundingClientRect();
                        return Math.abs(bounds.top - (innerHeight - $targetClearance)) < 2 && bounds.bottom <= innerHeight;
                    })();
                    """.trimIndent(),
                )
                scenario.onActivity {
                    HideViewOnScrollBehavior.from(navigation).slideIn(navigation, false)
                }
                var targetNode: AccessibilityNodeInfo? = null
                waitUntil(message = { "Statistics accessibility target did not appear" }) {
                    fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                        if (node == null) return null
                        if (node.contentDescription?.contains("Statistics accessibility target") == true ||
                            node.text?.contains("Statistics accessibility target") == true
                        ) {
                            return node
                        }
                        for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                        return null
                    }
                    targetNode = find(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
                    targetNode != null
                }
                webView.evaluate(
                    "window.statisticsLastScroll = performance.now(); window.addEventListener('scroll', () => { window.statisticsLastScroll = performance.now(); }); visualViewport.addEventListener('scroll', () => { window.statisticsLastScroll = performance.now(); });",
                )
                assertTrue(targetNode!!.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                assertTrue(targetNode!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id))
                webView.evaluate(
                    "window.statisticsFocusSettled = false; requestAnimationFrame(() => requestAnimationFrame(() => { window.statisticsFocusSettled = true; }));",
                )
                webView.awaitJavascript("window.statisticsFocusSettled === true && performance.now() - window.statisticsLastScroll > 500")
                // Focus scrolling may hide the bar. Keep it shown to check the same safe area.
                var safeBottomCssPixels = 0f
                scenario.onActivity {
                    HideViewOnScrollBehavior.from(navigation).slideIn(navigation, false)
                    safeBottomCssPixels = (navigation.yInWindow - webView.yInWindow) / statistics.webViewLayout.scale
                }
                val focusedControl =
                    JSONObject(
                        webView.evaluate(
                            """
                            (() => {
                                const target = document.getElementById('statistics-bottom-nav-focus-target');
                                const bounds = target.getBoundingClientRect();
                                return { focused: document.activeElement === target, top: bounds.top, bottom: bounds.bottom, visualHeight: visualViewport.height, visualScale: visualViewport.scale,
                                    offsetTop: window.visualViewport?.offsetTop || 0,
                                    };
                            })();
                            """.trimIndent(),
                        ),
                    )
                assertTrue(targetNode!!.refresh(), "The accessibility-focused control must remain attached")
                assertTrue(targetNode!!.isAccessibilityFocused, "The production control must have accessibility focus")
                val visualOffset = focusedControl.getDouble("offsetTop")

                assertTrue(focusedControl.getDouble("top") - visualOffset >= 0, "The focused control must remain on screen")
                assertTrue(
                    focusedControl.getDouble("bottom") - visualOffset <= safeBottomCssPixels + 1,
                    "Focused Statistics control $focusedControl must end above navigation at $safeBottomCssPixels CSS pixels",
                )
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
