// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.managespace

import android.content.Intent
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.FragmentSettingsBinding
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [ManageSpaceActivity] */
@RunWith(AndroidJUnit4::class)
class ManageSpaceInsetsTest : RobolectricTest() {
    @Test
    fun `the page is padded past a side navigation bar and cutout`() =
        withManageSpace { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.binding.root.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.binding.root.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the list scrolls under the navigation bar but its last row rests above it`() =
        withManageSpace { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.preferenceList.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the last row clears rounded display corners larger than the navigation bar`() =
        withManageSpace { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.preferenceList.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withManageSpace { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.preferenceList.paddingBottom, equalTo(0))
        }

    private val ManageSpaceActivity.manageSpaceFragment: ManageSpaceFragment
        get() = fragment as ManageSpaceFragment

    private val ManageSpaceActivity.binding: FragmentSettingsBinding
        get() = FragmentSettingsBinding.bind(manageSpaceFragment.requireView())

    private val ManageSpaceActivity.preferenceList: RecyclerView get() = manageSpaceFragment.listView

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun ManageSpaceActivity.dispatchInsets(
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

    private fun withManageSpace(block: (ManageSpaceActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                ManageSpaceActivity::class.java,
                Intent(targetContext, ManageSpaceActivity::class.java),
            )
        advanceRobolectricLooper()
        block(activity)
    }
}
