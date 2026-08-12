// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.utils.ext.windowInsetsControllerCompat
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiActivityTest : RobolectricTest() {
    @Test
    fun `nav bar icons are dark on a light nav bar`() {
        val activity = startRegularActivity<AnkiActivity>()

        // in the default (light) theme, toolbarBackgroundColor is light (#D6D7D7)
        activity.setNavigationBarColor(R.attr.toolbarBackgroundColor)

        assertThat(
            "a light navigation bar needs dark icons",
            activity.windowInsetsControllerCompat.isAppearanceLightNavigationBars,
            equalTo(true),
        )
    }

    @Test
    fun `nav bar icons are light on a dark nav bar - issue 20914`() {
        val activity = startRegularActivity<AnkiActivity>()

        // in the default (light) theme, showAnswerColor is dark (#455a64)
        // used by the Reviewer when the answer buttons are at the bottom
        activity.setNavigationBarColor(R.attr.showAnswerColor)

        assertThat(
            "a dark navigation bar needs light icons",
            activity.windowInsetsControllerCompat.isAppearanceLightNavigationBars,
            equalTo(false),
        )
    }
}
