// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.widget.TextView
import androidx.core.view.descendants
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.testutils.grantPermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class MultimediaActivityTest : RobolectricTest() {
    @Test
    fun `note context and result survive recreation and process death`() {
        grantPermissions(RECORD_AUDIO)
        val intent = largeNoteIntent().apply { replaceExtras(extras!!.parcelCopy()) }
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(MultimediaActivity::class.java, intent)

        controller.recreate()
        advanceRobolectricLooper()
        assertNotePreview(controller.get())

        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        val restored = restoreActivity(intent, savedState)
        val activity = restored.get()
        assertNotePreview(activity)

        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment
        val recording = File(targetContext.cacheDir, "recording.ogg").apply { writeText("audio") }
        fragment.viewModel.updateCurrentMultimediaPath(recording)
        fragment.viewModel.updateMediaFileLength(recording.length())
        advanceRobolectricLooper()
        fragment.binding.actionDone.performClick()

        val shadow = shadowOf(activity)
        val result = assertIs<MultimediaResult.Success>(MultimediaResultContract().parseResult(shadow.resultCode, shadow.resultIntent))
        assertThat(result.fieldIndex, equalTo(1))
        assertIs<AudioRecordingField>(result.field)
        assertThat(result.field.mediaFile, equalTo(recording))
        assertThat(result.field.hasTemporaryMedia, equalTo(true))
        restored.pause().stop().destroy()
    }

    private fun restoreActivity(
        intent: Intent,
        savedState: Bundle,
    ): ActivityController<MultimediaActivity> {
        val restoredState = savedState.parcelCopy()
        return Robolectric
            .buildActivity(MultimediaActivity::class.java, Intent(intent).apply { replaceExtras(intent.extras!!.parcelCopy()) })
            .create(restoredState)
            .start()
            .restoreInstanceState(restoredState)
            .postCreate(restoredState)
            .resume()
            .visible()
            .also {
                saveControllerForCleanup(it)
                advanceRobolectricLooper()
            }
    }

    private fun assertNotePreview(activity: MultimediaActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment
        val texts =
            fragment.binding.audioRecorderLayout.descendants
                .filterIsInstance<TextView>()
                .map { it.text.toString() }
                .toList()
        assertThat(texts.contains("front".repeat(30_000)), equalTo(true))
        assertThat(texts.contains("back"), equalTo(true))
    }

    private fun largeNoteIntent(): Intent {
        val note =
            MultimediaEditableNote().apply {
                setNumFields(2)
                setField(0, TextField().apply { text = "front".repeat(30_000) })
                setField(1, TextField().apply { text = "back" })
                freezeInitialFieldValues()
            }
        return AudioRecordingFragment.getIntent(
            targetContext,
            MultimediaActivityExtra(index = 1, field = AudioRecordingField(), note = note),
        )
    }

    private fun Bundle.parcelCopy(): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            parcel.readBundle(MultimediaActivity::class.java.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }
}
