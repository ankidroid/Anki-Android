// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.content.edit
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.noteeditor.getNoteEditorFragment
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.withSplitPaneUi
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Edge-to-edge inset handling for [NoteEditorActivity] */
@RunWith(AndroidJUnit4::class)
class NoteEditorInsetsTest : RobolectricTest() {
    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withNoteEditor { activity ->
            activity.dispatchInsets()

            assertThat(
                "app bar content is pushed clear of the status bar",
                activity.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
            assertThat(
                "the fragment root never takes the top inset",
                activity.fragmentRoot.paddingTop,
                equalTo(0),
            )
        }

    @Test
    fun `app bar is padded past a side navigation bar and cutout`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(activity.toolbarContainer.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(activity.toolbarContainer.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the fields are padded past a side navigation bar and cutout`() =
        withNoteEditor { activity ->
            val fields = activity.editorFields
            val baseLeft = fields.paddingLeft
            val baseRight = fields.paddingRight

            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(fields.paddingLeft, equalTo(baseLeft + 32.dp.toPx(targetContext)))
            assertThat(fields.paddingRight, equalTo(baseRight + 48.dp.toPx(targetContext)))
        }

    @Test
    fun `the formatting toolbar's colour reaches the side edges`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "the pane is not inset, or the toolbar would stop short of the screen edge",
                activity.editorFrame.paddingLeft + activity.editorFrame.paddingRight,
                equalTo(0),
            )
            assertThat(
                "the buttons are inset instead, so the toolbar's colour spans the screen",
                activity.formattingToolbar.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(activity.formattingToolbar.paddingRight, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the formatting toolbar's colour fills the navigation bar`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the toolbar draws behind the navigation bar, so its colour fills it",
                activity.formattingToolbar.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "the fragment root does not lift the toolbar, which would leave the strip bare",
                activity.fragmentRoot.paddingBottom,
                equalTo(0),
            )
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `the system does not scrim the button navigation bar`() =
        withNoteEditor { activity ->
            assertThat(
                "the toolbar's colour fills the bar, so a scrim over it would not match",
                activity.window.isNavigationBarContrastEnforced,
                equalTo(false),
            )
        }

    @Test
    fun `the fields stop above the toolbar, which is taller by the inset it fills`() =
        withNoteEditor { activity ->
            val height = targetContext.resources.getDimensionPixelSize(R.dimen.note_editor_toolbar_height)

            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                (activity.editorFields.layoutParams as MarginLayoutParams).bottomMargin,
                equalTo(height + 48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `with the formatting toolbar hidden the fields still clear the navigation bar`() {
        getPreferences().edit { putBoolean("noteEditorShowToolbar", false) }
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "no gap is reserved for a toolbar which is not shown, only the inset",
                (activity.editorFields.layoutParams as MarginLayoutParams).bottomMargin,
                equalTo(48.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `the formatting toolbar clears rounded display corners larger than the navigation bar`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)

            assertThat(activity.formattingToolbar.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)

            assertThat(activity.formattingToolbar.paddingBottom, equalTo(0))
        }

    @Test
    fun `the keyboard does not cover the formatting toolbar`() =
        withNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(activity.formattingToolbar.paddingBottom, equalTo(300.dp.toPx(targetContext)))
        }

    @Test
    fun `the two-pane editor does not reserve a gap beside the divider`() =
        withTabletNoteEditor { activity ->
            activity.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "the editor pane starts at the cutout",
                activity.formattingToolbar.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "the editor pane borders the divider, not the navigation bar",
                activity.formattingToolbar.paddingRight,
                equalTo(0),
            )
            assertThat(
                "the previewer pane clears the navigation bar",
                activity.previewerPane.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the previewer clears the navigation bar`() =
        withTabletNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp)

            assertThat(activity.previewerPane.paddingBottom, equalTo(48.dp.toPx(targetContext)))
        }

    @Test
    fun `the keyboard does not cover the previewer`() =
        withTabletNoteEditor { activity ->
            activity.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(activity.previewerPane.paddingBottom, equalTo(300.dp.toPx(targetContext)))
        }

    private val NoteEditorActivity.toolbarContainer: View get() = findViewById(R.id.toolbar_container)

    private val NoteEditorActivity.editorFrame: View get() = findViewById(R.id.note_editor_fragment_frame)

    private val NoteEditorActivity.previewerPane: View get() = findViewById(R.id.previewer_frame_layout)

    private val NoteEditorActivity.formattingToolbar: View get() = findViewById(R.id.editor_toolbar)

    private val NoteEditorActivity.editorFields: View get() = findViewById(R.id.note_editor_layout)

    private val NoteEditorActivity.fragmentRoot: View get() = getNoteEditorFragment().requireView()

    /** Dispatches realistic system-bar insets, which Robolectric otherwise reports as zero. */
    private fun NoteEditorActivity.dispatchInsets(
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
                            setRoundedCorner(
                                RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                                RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                            )
                        }
                    }.build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
    }

    private fun withNoteEditor(block: (NoteEditorActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.AddNote().toIntent(targetContext),
            )
        advanceRobolectricLooper()
        block(activity)
    }

    private fun withTabletNoteEditor(block: (NoteEditorActivity) -> Unit) = withSplitPaneUi { withNoteEditor(block) }
}
