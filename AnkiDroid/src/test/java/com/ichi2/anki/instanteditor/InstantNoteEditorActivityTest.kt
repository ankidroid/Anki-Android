// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.instanteditor

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstantNoteEditorActivityTest : RobolectricTest() {
    @Test
    fun `tapping Save twice while the note is saving adds one note`() =
        runTest {
            startInstantNoteEditor("{{c1::Hello}} world")
            val notesBefore = col.noteCount()

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                repeat(2) {
                    onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                }
            }
            advanceUntilIdle()

            assertEquals("notes added", notesBefore + 1, col.noteCount())
        }

    @Test
    fun `back while the note is saving does not close the editor`() =
        runTest {
            val activity = startInstantNoteEditor("{{c1::Hello}} world")

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                activity.onBackPressedDispatcher.onBackPressed()

                assertFalse("editor closed while saving", activity.isFinishing)
            }
            advanceUntilIdle()

            assertTrue("editor closes once the note is saved", activity.isFinishing)
        }

    private suspend fun startInstantNoteEditor(text: String): InstantNoteEditorActivity {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        return withCollectionAccessOn(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) {
            startRegularActivity<InstantNoteEditorActivity>(intent)
        }
    }

    private suspend fun <T> withCollectionAccessOn(
        dispatcher: CoroutineDispatcher,
        block: suspend () -> T,
    ): T {
        val previousQueue = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }
}
