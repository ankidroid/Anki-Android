// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.view.View
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.ActivityMultimediaBinding
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [MultimediaActivity], applied by [MultimediaFragment]. */
@RunWith(AndroidJUnit4::class)
class MultimediaInsetsTest : RobolectricTest() {
    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withMultimedia { activity ->
            activity.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                activity.binding.root.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                activity.binding.appBar.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar is padded past a side navigation bar and cutout`() =
        withMultimedia { activity ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.binding.appBar.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.binding.appBar.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the fragment clears a side navigation bar and cutout`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.fragmentRoot.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.fragmentRoot.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the done button clears the navigation bar`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the done button clears rounded display corners larger than the navigation bar`() =
        withMultimedia { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withMultimedia { activity ->
            // the bar is wider than the corner radius, so no vertical buffer is needed
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.fragmentRoot.paddingBottom, equalTo(0))
        }

    private val MultimediaActivity.binding: ActivityMultimediaBinding
        get() = ActivityMultimediaBinding.bind(findViewById(R.id.root_layout))

    /** The hosted fragment's own root, which [MultimediaFragment] insets */
    private val MultimediaActivity.fragmentRoot: View
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container)!!.requireView()

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun MultimediaActivity.dispatchInsets(
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

    private fun withMultimedia(block: (MultimediaActivity) -> Unit) {
        grantPermissions(RECORD_AUDIO)
        // the recorder reads the note's initial field values, so the note needs a frozen field
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, TextField().apply { text = "Front of the card" })
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(index = 0, field = AudioRecordingField(), note = note)
        block(
            startActivityNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                AudioRecordingFragment.getIntent(targetContext, extra),
            ),
        )
    }
}
