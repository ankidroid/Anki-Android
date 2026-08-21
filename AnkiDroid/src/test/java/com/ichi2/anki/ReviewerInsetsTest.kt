// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge inset handling for the legacy [Reviewer].
 *
 * The app bar's background spans the full width and draws behind the status bar; only its
 * content is inset. The counts bar, card and answer area are held inside the safe area, with
 * the answer area's background extending underneath the navigation bar.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerInsetsTest : RobolectricTest() {
    // rendering a card requires a media folder
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                reviewer.rootLayout.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                reviewer.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar and counts bar are padded past a side navigation bar and cutout`() =
        withReviewer { reviewer ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            reviewer.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "app bar content clears the cutout",
                reviewer.toolbarContainer.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "app bar content clears the side navigation bar",
                reviewer.toolbarContainer.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )

            val sideMargin = targetContext.resources.getDimensionPixelSize(R.dimen.side_margin)
            assertThat(
                "the counts bar keeps its side margin past the cutout",
                reviewer.countsBar.paddingLeft,
                equalTo(32.dp.toPx(targetContext) + sideMargin),
            )
            assertThat(
                "the counts bar keeps its side margin past the navigation bar",
                reviewer.countsBar.paddingRight,
                equalTo(48.dp.toPx(targetContext) + sideMargin),
            )
        }

    @Test
    fun `the card is padded past a side navigation bar and cutout`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat("card clears the cutout", reviewer.cardContainer.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(
                "card clears the side navigation bar",
                reviewer.cardContainer.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `answer buttons clear the navigation bar`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the answer area rests a navigation bar's height above the screen's bottom",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "the card sits above the answer area and needs no bottom inset",
                reviewer.cardContainer.paddingBottom,
                equalTo(0),
            )
        }

    @Test
    fun `answer buttons at the top - the card clears the navigation bar`() =
        withReviewer(answerButtonsPosition = "top") { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the card is the bottom-most element and clears the navigation bar",
                reviewer.cardContainer.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "the answer area is at the top and needs no bottom inset",
                reviewer.answerArea.paddingBottom,
                equalTo(0),
            )
        }

    @Test
    fun `no answer buttons - the type-answer area clears the navigation bar`() =
        withReviewer(answerButtonsPosition = "none") { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the bottom area (holding the type-answer field) clears the navigation bar",
                reviewer.bottomArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the answer area clears the keyboard`() =
        withReviewer { reviewer ->
            // edge-to-edge disables adjustResize: the answer buttons - and the type-answer
            // field above them - are kept above the keyboard via the ime inset
            reviewer.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(
                "the answer area rests above the keyboard",
                reviewer.answerArea.paddingBottom,
                equalTo(300.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `fullscreen review keeps the legacy fitsSystemWindows handling`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the answer area is padded by android:fitsSystemWindows, not by a listener",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "fitsSystemWindows also applies the top inset",
                reviewer.answerArea.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }
    }

    private val Reviewer.rootLayout: View
        get() = findViewById(R.id.root_layout)

    private val Reviewer.toolbarContainer: View
        get() = findViewById(R.id.toolbar_container)

    private val Reviewer.countsBar: View
        get() = findViewById(R.id.top_bar)

    private val Reviewer.cardContainer: View
        get() = findViewById(R.id.flashcard)

    private val Reviewer.bottomArea: View
        get() = findViewById(R.id.bottom_area_layout)

    private val Reviewer.answerArea: View
        get() = findViewById(R.id.answer_options_layout)

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun Reviewer.dispatchInsets(
        navBarBottom: Dp = 0.dp,
        navBarRight: Dp = 0.dp,
        cutoutLeft: Dp = 0.dp,
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
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
    }

    private fun withReviewer(
        answerButtonsPosition: String? = null,
        block: (Reviewer) -> Unit,
    ) {
        answerButtonsPosition?.let { position ->
            targetContext.sharedPrefs().edit {
                putString(targetContext.getString(R.string.answer_buttons_position_preference), position)
            }
        }
        addBasicNote("Hello", "World")
        block(ReviewerTest.startReviewer(this))
    }
}
