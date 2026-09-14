// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

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
class StatisticsBottomNavigationFocusTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun focusedStatisticsControlClearsVisibleBottomNavigation() {
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
                scenario.onActivity { webView.requestFocus() }
                webView.awaitJavascript("document.hasFocus()")
                var coveredCssPixels = 0f
                scenario.onActivity {
                    coveredCssPixels = (webView.yInWindow + webView.height - navigation.yInWindow) / statistics.webViewLayout.scale
                }
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
                            document.scrollingElement.scrollTop += target.getBoundingClientRect().top
                                - (window.innerHeight - $coveredCssPixels / 2);
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
                        return bounds.top > innerHeight - $coveredCssPixels && bounds.bottom <= innerHeight;
                    })();
                    """.trimIndent(),
                )
                scenario.onActivity {
                    HideViewOnScrollBehavior.from(navigation).slideIn(navigation, false)
                }
                webView.evaluate(
                    """
                    window.statisticsFocusSettled = false;
                    document.getElementById('statistics-bottom-nav-focus-target').focus();
                    requestAnimationFrame(() => requestAnimationFrame(() => { window.statisticsFocusSettled = true; }));
                    """.trimIndent(),
                )
                webView.awaitJavascript("window.statisticsFocusSettled === true")
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
                                return { focused: document.activeElement === target, top: bounds.top, bottom: bounds.bottom };
                            })();
                            """.trimIndent(),
                        ),
                    )
                assertTrue(focusedControl.getBoolean("focused"), "The production graph control must have focus")
                assertTrue(focusedControl.getDouble("top") >= 0, "The focused control must remain on screen")
                assertTrue(
                    focusedControl.getDouble("bottom") <= safeBottomCssPixels + 1,
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
