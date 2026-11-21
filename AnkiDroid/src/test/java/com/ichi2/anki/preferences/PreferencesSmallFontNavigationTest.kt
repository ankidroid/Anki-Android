// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class PreferencesSmallFontNavigationTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "sw560dp-w560dp-h900dp")
    fun `compact settings show the category list with a small font`() {
        RuntimeEnvironment.setFontScale(0.85f)
        val activity = startRegularActivity<PreferencesActivity>(PreferencesActivity.getIntent(targetContext))
        val settings = (activity.fragment as PreferencesFragment).childFragmentManager

        assertIs<HeaderFragment>(settings.findFragmentById(R.id.settings_container))
    }
}
