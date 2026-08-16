// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge inset handling for [Info].
 *
 * The app bar's background spans the full width and draws behind the status bar; only its content
 * is inset. Everything below the app bar is held inside the safe area: 'donate' is the bottom-most
 * touch target, so it clears both the navigation bar and the rounded display corners.
 */
@RunWith(AndroidJUnit4::class)
class InfoInsetsTest : RobolectricTest() {
    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withInfo { info ->
            info.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                info.rootLayout.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                info.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar is padded past a side navigation bar and cutout`() =
        withInfo { info ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            info.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "app bar content clears the cutout",
                info.toolbarContainer.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "app bar content clears the side navigation bar",
                info.toolbarContainer.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `content is padded past a side navigation bar and cutout`() =
        withInfo { info ->
            info.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "content clears the cutout",
                info.content.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "content clears the side navigation bar",
                info.content.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `bottom controls clear the navigation bar`() =
        withInfo { info ->
            info.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "'donate' rests a navigation bar's height above the screen's bottom",
                info.content.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `bottom controls clear rounded display corners larger than the navigation bar`() =
        withInfo { info ->
            info.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(
                "'donate' rests above the corner arc, not just the navigation bar",
                info.content.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withInfo { info ->
            // landscape with 3-button navigation: the bar is wider than the corner radius, so the
            // controls are already inboard of the corner arc and no vertical buffer is needed
            info.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat("no bottom buffer is reserved", info.content.paddingBottom, equalTo(0))
        }

    private val Info.rootLayout: View
        get() = findViewById(R.id.root_layout)

    private val Info.toolbarContainer: View
        get() = findViewById(R.id.toolbar_container)

    private val Info.content: View
        get() = findViewById(R.id.content)

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun Info.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
        bottomCornerRadius: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarRight, bottom = navBarBottom))
                    .setInsets(displayCutout(), insetsOf(left = cutoutLeft))
                    .apply {
                        val radius = bottomCornerRadius.toPx(targetContext)
                        if (radius > 0) {
                            // only the radius is read by the implementation; the center is unused
                            setRoundedCorner(
                                RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                                RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                            )
                        }
                    }.build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
    }

    private fun withInfo(block: (Info) -> Unit) =
        block(
            startActivityNormallyOpenCollectionWithIntent(
                Info::class.java,
                Intent(targetContext, Info::class.java),
            ),
        )
}
