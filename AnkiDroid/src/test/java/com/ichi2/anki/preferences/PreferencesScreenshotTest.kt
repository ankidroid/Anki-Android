// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.preferences

import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import com.google.android.material.appbar.AppBarLayout
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.HIDDEN_GESTURE_BAR
import com.ichi2.testutils.ext.clear
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.utils.dp
import org.junit.After
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.reflect.KClass

class PreferencesScreenshotTest : ScreenshotTest() {
    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.clear()
    }

    @Test
    fun headerFragmentOnPortrait() =
        withPreferencesActivity(HeaderFragment::class) { activity ->
            activity.simulateSystemBars()
            captureScreen("HeaderFragment_portrait")
        }

    @Test
    fun headerFragmentOnLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withPreferencesActivity(HeaderFragment::class) { activity ->
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("HeaderFragment_landscape")
        }
    }

    @Test
    fun headerFragmentGestureBarHiddenScrolledToBottom() =
        withPreferencesActivity(HeaderFragment::class) { activity ->
            activity.simulateSystemBars(navBarBottom = HIDDEN_GESTURE_BAR, bottomCornerRadius = 34.dp)
            activity.scrollSettingsToBottom()
            captureScreen("HeaderFragment_gesture_bar_hidden_scrolled_to_bottom")
        }

    @Test
    fun headerFragmentGestureBarScrolledToBottom() =
        withPreferencesActivity(HeaderFragment::class) { activity ->
            activity.simulateSystemBars(navBarBottom = 24.dp, bottomCornerRadius = 34.dp)
            activity.scrollSettingsToBottom()
            captureScreen("HeaderFragment_gesture_bar_scrolled_to_bottom")
        }

    @Test
    fun `capture all preference fragments`() {
        val fragments = PreferenceTestUtils.getAllPreferencesFragments(targetContext)

        fragments.forEach { fragment ->
            val fragmentClass = fragment::class
            withPreferencesActivity(fragmentClass) { activity ->
                Prefs.isNewStudyScreenEnabled = true
                activity.stabilizeContent()
                captureScreen(fragmentClass.simpleName!!)
            }
        }
    }

    /**
     * Scrolls the displayed settings list to its end, where its bottom padding is visible.
     *
     * The app bar is collapsed first, as a user's scroll would: while it is expanded, the list
     * extends below the screen by the app bar's scroll range, which hides the padding.
     */
    private fun PreferencesActivity.scrollSettingsToBottom() {
        findViewById<AppBarLayout>(R.id.appbar).setExpanded(false, false)
        advanceRobolectricLooper()
        val mainFragment = fragment as PreferencesFragment
        val settingsFragment = mainFragment.childFragmentManager.findFragmentById(R.id.settings_container) as SettingsFragment
        val list = settingsFragment.listView
        list.scrollToPosition(list.adapter!!.itemCount - 1)
        while (list.canScrollVertically(1)) list.scrollBy(0, 50)
        advanceRobolectricLooper()
    }

    /** Replaces content which changes between runs, so it does not appear in diffs */
    private fun PreferencesActivity.stabilizeContent() {
        val settingsFragment = settingsFragment
        // Robolectric generates a different temporary path every time,
        // so avoid creating an unnecessary diff in the collection path pref summary
        (settingsFragment as? AdvancedSettingsFragment)?.apply {
            requirePreference<Preference>(CollectionHelper.PREF_COLLECTION_PATH).summaryProvider = {
                "/storage/emulated/0/AnkiDroid"
            }
        }
        (settingsFragment as? AboutFragment)?.apply {
            binding.buildDate.text = "May 18, 2026"
        }
    }

    private fun withPreferencesActivity(
        fragmentClass: KClass<out Fragment>,
        block: (PreferencesActivity) -> Unit,
    ) {
        val intent = PreferencesActivity.getIntent(targetContext, fragmentClass)
        ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                block(activity)
            }
        }
    }

    /** The fragment displayed in the settings pane */
    private val PreferencesActivity.settingsFragment: Fragment?
        get() = (fragment as PreferencesFragment).childFragmentManager.findFragmentById(R.id.settings_container)
}
