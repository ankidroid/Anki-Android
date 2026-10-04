// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.internationalization

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.PreviewTranslations
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposableTranslationsTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `proper translations implementation is used in composables`() {
        composeTestRule.setContent {
            // uses expected implementation for preview mode
            CompositionLocalProvider(LocalInspectionMode provides true) {
                assertThat(tr(), instanceOf(PreviewTranslations::class.java))
            }
            // reverts to using the backend implementation for normal mode
            CompositionLocalProvider(LocalInspectionMode provides false) {
                assertThat(tr(), instanceOf(BackendTranslations::class.java))
            }
        }
    }
}
