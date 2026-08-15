// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.worker

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ichi2.anki.NOTIFICATION_MIN_DELAY_MS
import com.ichi2.anki.receiver.CopyToClipboardReceiver
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SyncMediaWorkerTest {
    private lateinit var worker: SyncMediaWorker

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        worker = TestListenableWorkerBuilder<SyncMediaWorker>(context).build()
    }

    @Test
    @Suppress("SimplifyBooleanWithConstants")
    fun `notification update delay is not lower than min delay`() {
        assert(SyncMediaWorker.NOTIFICATION_UPDATE_RATE_MS >= NOTIFICATION_MIN_DELAY_MS)
    }

    // https://github.com/ankidroid/Anki-Android/issues/20826
    @Test
    fun `copy to clipboard intent is immutable`() {
        val pendingIntent = worker.getCopyToClipboardIntent("error text")

        assertThat(
            "an intent attached to a notification must not be modifiable by other apps",
            shadowOf(pendingIntent).flags and PendingIntent.FLAG_IMMUTABLE,
            not(equalTo(0)),
        )
    }

    // https://github.com/ankidroid/Anki-Android/issues/20826
    @Test
    fun `error text is trimmed to fit in the binder transaction buffer`() {
        val hugeText = "e".repeat(SyncMediaWorker.MAX_ERROR_TEXT_LENGTH + 1)

        val pendingIntent = worker.getCopyToClipboardIntent(hugeText)

        val errorText =
            shadowOf(pendingIntent)
                .savedIntent
                .getStringExtra(CopyToClipboardReceiver.EXTRA_SYNC_ERROR_LOG)
        assertThat(errorText?.length, equalTo(SyncMediaWorker.MAX_ERROR_TEXT_LENGTH))
    }
}
