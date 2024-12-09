/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.ichi2.anki.multimedia.AudioRecordingFragment
import com.ichi2.anki.multimedia.AudioVideoFragment
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaActivityExtra
import com.ichi2.anki.multimedia.MultimediaFragment
import com.ichi2.anki.multimedia.MultimediaImageFragment
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class MultimediaTest : InstrumentedTest() {

    @JvmField
    @Parameterized.Parameter(0)
    var intentBuilder: (Context) -> Intent? = { null }

    private var title: Int? = null

    @Test
    fun testFragmentTitle() {
        withMultimediaActivityScenario { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaFragment
                val titleString = title?.let { testContext.getString(it) }
                assertEquals(titleString, fragment.title)
            }
        }
    }

    @Test
    fun testImageFragmentDiscardDialogShown() {
        val validIntentBuilders = setOf(
            { context: Context -> getCameraFragment(context) },
            { context: Context -> getGalleryFragment(context) },
            { context: Context -> getDrawingFragment(context) }
        )

        if (intentBuilder !in validIntentBuilders) {
            return
        }

        withMultimediaActivityScenario { scenario ->
            scenario.onActivity { activity ->
                (activity.fragmentContainer as MultimediaImageFragment).apply {
                    viewModel.updateCurrentMultimediaPath("test/path")
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }

            onView(withText(CollectionManager.TR.addingDiscardCurrentInput()))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))

            onView(withText(R.string.discard))
                .check(matches(isDisplayed()))

            onView(withText(CollectionManager.TR.addingKeepEditing()))
                .check(matches(isDisplayed()))
        }
    }

    /** Runs [ActivityScenario.launch] with the result of the [intentBuilder] */
    private fun withMultimediaActivityScenario(block: (ActivityScenario<MultimediaActivity>) -> Unit) {
        ActivityScenario.launch<MultimediaActivity>(intentBuilder(testContext)).use { block(it) }
    }

    private val MultimediaActivity.fragmentContainer: Fragment
        get() = this.supportFragmentManager.findFragmentById(R.id.fragment_container)!!

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic
        fun initParameters(): Collection<Array<out Any>> {
            return listOf(
                arrayOf({ context: Context -> getCameraFragment(context) }, R.string.multimedia_editor_popup_image),
                arrayOf({ context: Context -> getGalleryFragment(context) }, R.string.multimedia_editor_popup_image),
                arrayOf({ context: Context -> getDrawingFragment(context) }, R.string.multimedia_editor_popup_image),
                arrayOf({ context: Context -> getAudioFragment(context) }, R.string.multimedia_editor_popup_audio_clip),
                arrayOf({ context: Context -> getVideoFragment(context) }, R.string.multimedia_editor_popup_video_clip),
                arrayOf({ context: Context -> getAudioRecordingFragment(context) }, R.string.multimedia_editor_field_editing_audio)
            )
        }

        private val multimediaActivityExtra = MultimediaActivityExtra(0, ImageField(), getTestMultimediaNote())

        private fun getCameraFragment(context: Context): Intent {
            return MultimediaImageFragment.getIntent(
                context,
                multimediaActivityExtra,
                MultimediaImageFragment.ImageOptions.CAMERA
            )
        }

        private fun getGalleryFragment(context: Context): Intent {
            return MultimediaImageFragment.getIntent(
                context,
                multimediaActivityExtra,
                MultimediaImageFragment.ImageOptions.GALLERY
            )
        }

        private fun getDrawingFragment(context: Context): Intent {
            return MultimediaImageFragment.getIntent(
                context,
                multimediaActivityExtra,
                MultimediaImageFragment.ImageOptions.DRAWING
            )
        }

        private fun getVideoFragment(context: Context): Intent {
            return AudioVideoFragment.getIntent(
                context,
                multimediaActivityExtra,
                AudioVideoFragment.MediaOption.VIDEO_CLIP
            )
        }

        private fun getAudioFragment(context: Context): Intent {
            return AudioVideoFragment.getIntent(
                context,
                multimediaActivityExtra,
                AudioVideoFragment.MediaOption.AUDIO_CLIP
            )
        }

        private fun getAudioRecordingFragment(context: Context): Intent {
            return AudioRecordingFragment.getIntent(
                context,
                multimediaActivityExtra
            )
        }

        private fun getTestMultimediaNote(): MultimediaEditableNote {
            val note = MultimediaEditableNote()
            note.setNumFields(1)
            note.setField(0, TextField())
            note.freezeInitialFieldValues()
            return note
        }
    }
}
