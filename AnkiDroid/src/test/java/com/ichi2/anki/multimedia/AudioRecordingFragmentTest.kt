// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.testutils.grantPermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Tests for [AudioRecordingFragment]. */
@RunWith(AndroidJUnit4::class)
class AudioRecordingFragmentTest : RobolectricTest() {
    @Test
    fun `done button is disabled when no media path is set`() =
        withAudioRecordingFragment { fragment ->
            assertThat("Done button should be disabled initially", fragment.binding.actionDone.isEnabled, equalTo(false))
        }

    @Test
    fun `done button is enabled when media path is set`() =
        withAudioRecordingFragment { fragment ->
            fragment.viewModel.updateCurrentMultimediaPath(File("dummy_path"))

            assertThat("Done button should be enabled after media path is set", fragment.binding.actionDone.isEnabled, equalTo(true))
        }

    @Test
    fun `audio recording controller is correctly initialized`() =
        withAudioRecordingFragment { fragment ->
            assertThat(
                "Audio recording controller should inflate into audio_recorder_layout",
                fragment.binding.audioRecorderLayout.childCount,
                greaterThan(0),
            )
        }

    private fun withAudioRecordingFragment(block: (AudioRecordingFragment) -> Unit) {
        grantPermissions(RECORD_AUDIO)
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, TextField().apply { text = "Front of the card" })
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(index = 0, field = AudioRecordingField(), note = note)
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                AudioRecordingFragment.getIntent(targetContext, extra),
            )
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment
        block(fragment)
    }
}
