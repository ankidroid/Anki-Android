// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.account

import android.view.View
import android.widget.Button
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.FragmentPageBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [AccountActivity], applied by its hosted fragments. */
@RunWith(AndroidJUnit4::class)
class AccountInsetsTest : RobolectricTest() {
    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withLoginScreen { activity ->
            activity.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                activity.rootLayout.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                activity.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar is padded past a side navigation bar and cutout`() =
        withLoginScreen { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.toolbarContainer.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.toolbarContainer.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the form clears the navigation bar`() =
        withLoginScreen { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.content.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the form clears rounded display corners larger than the navigation bar`() =
        withLoginScreen { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.content.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the keyboard does not cover the password field`() =
        withLoginScreen { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(activity.content.paddingBottom, equalTo(300.dp.toPx(targetContext)))
        }

    @Test
    fun `the logged in screen is inset too`() =
        withLoggedInScreen { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "app bar content is pushed clear of the status bar",
                activity.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
            assertThat(activity.content.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the remove account toolbar is pushed clear of the status bar`() =
        withRemoveAccountScreen { activity ->
            activity.dispatchInsets()

            assertThat(
                "the toolbar no longer draws under the status bar",
                activity.removeAccountBinding.root.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the remove account page is padded past a side navigation bar and cutout`() =
        withRemoveAccountScreen { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.removeAccountBinding.root.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.removeAccountBinding.root.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the remove account page clears the navigation bar`() =
        withRemoveAccountScreen { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                activity.removeAccountBinding.webviewContainer.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the remove account page clears rounded display corners larger than the navigation bar`() =
        withRemoveAccountScreen { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(
                activity.removeAccountBinding.webviewContainer.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the keyboard does not cover the remove account form`() =
        withRemoveAccountScreen { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(
                activity.removeAccountBinding.webviewContainer.paddingBottom,
                equalTo(300.dp.toPx(targetContext)),
            )
        }

    /** The activity's own root, outside the fragment. Note the fragment root shares this id. */
    private val AccountActivity.rootLayout: View get() = findViewById(R.id.root_layout)

    private val AccountActivity.toolbarContainer: View get() = findViewById(R.id.toolbar_container)

    private val AccountActivity.content: View get() = findViewById(R.id.account_content)

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun AccountActivity.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
        bottomCornerRadius: Dp = 0.dp,
        imeBottom: Dp = 0.dp,
    ) {
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarRight, bottom = navBarBottom))
                    .setInsets(displayCutout(), insetsOf(left = cutoutLeft))
                    .setInsets(ime(), insetsOf(bottom = imeBottom))
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

    private fun withLoginScreen(block: (AccountActivity) -> Unit) {
        Prefs.hkey = ""
        block(startAccountActivity())
    }

    private fun withLoggedInScreen(block: (AccountActivity) -> Unit) {
        Prefs.hkey = "my precious hkey"
        Prefs.username = "lovely@example.com"
        block(startAccountActivity())
    }

    private val AccountActivity.removeAccountBinding: FragmentPageBinding
        get() =
            FragmentPageBinding.bind(
                supportFragmentManager.findFragmentById(R.id.remove_account_frame)!!.requireView(),
            )

    private fun withRemoveAccountScreen(block: (AccountActivity) -> Unit) =
        withLoggedInScreen { activity ->
            // show the fragment first: insets are only received by attached views
            activity.findViewById<Button>(R.id.remove_account_button).performClick()
            advanceRobolectricLooper()
            block(activity)
        }

    private fun startAccountActivity(): AccountActivity =
        startActivityNormallyOpenCollectionWithIntent(
            AccountActivity::class.java,
            AccountActivity.getIntent(targetContext),
        )
}
