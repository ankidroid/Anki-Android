// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.receiver

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Parcel
import android.service.chooser.ChooserResult
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ExportSharedReceiverRegressionTest : RobolectricTest(loadBackend = false) {
    @Test
    fun `Android 16 same profile chooser result control postpones the reminder`() {
        val sender = assertNotNull(ExportSharedReceiver.getIntentSender(targetContext, File(tempFolder.root, "collection.colpkg")))
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share", sender)
        val callback =
            assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER, IntentSender::class.java))
        val parcel = Parcel.obtain()
        val result =
            try {
                parcel.writeInt(0) // CHOOSER_RESULT_SELECTED_COMPONENT
                ComponentName.writeToParcel(ComponentName("com.example.drive", "UploadActivity"), parcel)
                parcel.writeBoolean(false)
                parcel.setDataPosition(0)
                ChooserResult.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }

        callback.sendIntent(
            targetContext,
            Activity.RESULT_OK,
            Intent()
                .putExtra(
                    Intent.EXTRA_CHOOSER_RESULT,
                    result,
                ).putExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName("com.example.drive", "UploadActivity")),
            null,
            null,
        )
        advanceRobolectricLooper()

        assertTrue(
            getPreferences().getLong("timeToShowBackupDialog", 0) > 0,
            "Real Android 16 same profile callback must postpone the reminder",
        )
    }

    @Test
    fun `Android 16 work profile share postpones the reminder`() {
        val sender = assertNotNull(ExportSharedReceiver.getIntentSender(targetContext, File(tempFolder.root, "collection.colpkg")))
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share", sender)
        val callback =
            assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER, IntentSender::class.java))
        val parcel = Parcel.obtain()
        val result =
            try {
                parcel.writeInt(-1) // CHOOSER_RESULT_UNKNOWN: Android redacts cross profile targets.
                ComponentName.writeToParcel(null, parcel)
                parcel.writeBoolean(false)
                parcel.setDataPosition(0)
                ChooserResult.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }

        callback.sendIntent(targetContext, Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_CHOOSER_RESULT, result), null, null)
        advanceRobolectricLooper()

        assertTrue(getPreferences().getLong("timeToShowBackupDialog", 0) > 0, "Completed share to work profile must postpone the reminder")
    }

    @Test
    fun `Android 16 copy action keeps the reminder`() {
        val sender = assertNotNull(ExportSharedReceiver.getIntentSender(targetContext, File(tempFolder.root, "collection.colpkg")))
        val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share", sender)
        val callback =
            assertNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER, IntentSender::class.java))
        val parcel = Parcel.obtain()
        val result =
            try {
                parcel.writeInt(ChooserResult.CHOOSER_RESULT_COPY)
                ComponentName.writeToParcel(null, parcel)
                parcel.writeBoolean(false)
                parcel.setDataPosition(0)
                ChooserResult.CREATOR.createFromParcel(parcel)
            } finally {
                parcel.recycle()
            }

        callback.sendIntent(targetContext, Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_CHOOSER_RESULT, result), null, null)
        advanceRobolectricLooper()

        assertEquals(0L, getPreferences().getLong("timeToShowBackupDialog", 0))
    }

    @Test
    fun `legacy callback control postpones the reminder`() {
        val sender = assertNotNull(ExportSharedReceiver.getIntentSender(targetContext, File(tempFolder.root, "collection.colpkg")))
        val result = Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName("com.example.drive", "UploadActivity"))

        sender.sendIntent(targetContext, Activity.RESULT_OK, result, null, null)
        advanceRobolectricLooper()

        assertTrue(getPreferences().getLong("timeToShowBackupDialog", 0) > 0)
    }
}
