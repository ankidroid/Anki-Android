// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.testutils.windowInsetsOf
import com.ichi2.themes.Themes
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogInsetsTest : RobolectricTest() {
    @Test
    fun `toolbar content clears the status bar`() =
        withDialog { dialog ->
            dialog.dispatchInsets()

            assertThat(dialog.toolbarContainer.paddingTop, equalTo(24.dp.toPx(targetContext)))
        }

    @Test
    fun `toolbar and content clear a side navigation bar and cutout`() =
        withDialog { dialog ->
            dialog.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            for (view in listOf(dialog.toolbarContainer, dialog.content)) {
                assertThat(view.paddingLeft, equalTo(32.dp.toPx(targetContext)))
                assertThat(view.paddingRight, equalTo(48.dp.toPx(targetContext)))
            }
        }

    @Test
    fun `content clears the navigation bar`() =
        withDialog { dialog ->
            dialog.dispatchInsets(navBarBottom = 48.dp)

            assertThat(dialog.content.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `status bar icons are dark over a light theme`() =
        withDialog(DayTheme.LIGHT) { dialog ->
            assertThat(dialog.hasLightStatusBars, equalTo(true))
        }

    @Test
    fun `status bar icons stay light over a dark theme`() =
        withDialog(NightTheme.DARK) { dialog ->
            assertThat(dialog.hasLightStatusBars, equalTo(false))
        }

    private val ChangeNoteTypeDialog.toolbarContainer: View
        get() = requireView().findViewById(R.id.toolbar_container)

    private val ChangeNoteTypeDialog.content: View
        get() = requireView().findViewById(R.id.change_note_type_layout)

    private val ChangeNoteTypeDialog.hasLightStatusBars: Boolean
        get() {
            val window = requireDialog().window!!
            return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        }

    private fun ChangeNoteTypeDialog.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                windowInsetsOf(navBarBottom = navBarBottom, navBarRight = navBarRight, cutoutLeft = cutoutLeft)
            }
        ViewCompat.dispatchApplyWindowInsets(requireDialog().window!!.decorView, insets)
    }

    private fun withDialog(
        theme: Theme = DayTheme.LIGHT,
        block: (ChangeNoteTypeDialog) -> Unit,
    ) {
        val previousTheme = Themes.currentTheme
        Themes.currentTheme = theme
        try {
            launchFragment<ChangeNoteTypeDialog>(
                fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(addBasicNote().id)) },
                themeResId = theme.styleResId,
            ).use { scenario -> scenario.onFragment(block) }
        } finally {
            Themes.currentTheme = previousTheme
        }
    }
}
