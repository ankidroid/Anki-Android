// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.ContentInfo
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import anki.generic.Empty
import anki.import_export.exportAnkiPackageOptions
import anki.import_export.exportLimit
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.ImportDialog
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ImportRoutingTest : RobolectricTest() {
    private var filenameQueries = 0

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        // Robolectric includes the test name in this path; avoid an extra subdirectory for Windows SQLite.
        val collectionDirectory = assertNotNull(targetContext.getExternalFilesDir(null))
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, collectionDirectory.absolutePath)
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun `dropped package retains its payload until preparation completes`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage().copyTo(File(tempFolder.root, "deck with spaces.apkg"))
            val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val uri = assertNotNull(provideFile(source, "application/apkg").data)
            val ioScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(ioScheduler)

            val payload = dropFile(activity, uri)
            shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
                repeat(3) { System.gc() }
                assertNotNull(payload.get(), "The payload must remain alive when the source is opened")
                source.inputStream()
            }
            repeat(3) { System.gc() }
            try {
                assertEquals(0, filenameQueries, "Preparation must still be queued")
                assertNotNull(payload.get(), "Keep the platform payload alive while its URI permissions are needed")
            } finally {
                ioScheduler.runCurrent()
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertCachedPackage(activity, source)
            assertPayloadReleased(payload)
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun `cancelled dropped import releases its payload`() =
        runTest {
            setIntroductionSlidesShown(true)
            val source = exportPackage()
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val activity = controller.get()
            val uri = assertNotNull(provideFile(source, "application/apkg").data)
            val ioScheduler = TestCoroutineScheduler()
            ioDispatcher = StandardTestDispatcher(ioScheduler)
            val payload = dropFile(activity, uri)

            activity.finish()
            controller.pause().stop().destroy()
            ioScheduler.runCurrent()

            assertEquals(0, filenameQueries, "A cancelled import must not start preparation")
            assertPayloadReleased(payload)
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dropFile(
        activity: DeckPicker,
        uri: Uri,
    ): WeakReference<ContentInfo> {
        val payload =
            ContentInfoCompat
                .Builder(ClipData.newRawUri("deck", uri), ContentInfoCompat.SOURCE_DRAG_AND_DROP)
                .build()
        ViewCompat.performReceiveContent(activity.findViewById(R.id.pull_to_sync_wrapper), payload)
        return WeakReference(assertNotNull(payload.toContentInfo()))
    }

    private fun assertPayloadReleased(payload: WeakReference<ContentInfo>) {
        repeat(10) {
            System.gc()
            if (payload.get() == null) return
        }
        assertNull(payload.get(), "Release the payload when its import finishes or is cancelled")
    }

    private fun exportPackage(legacy: Boolean = false): File {
        if (col.noteCount() == 0) addBasicNote("Issue 21430", "round-trip")
        return tempFolder.newFile(if (legacy) "legacy.APKG" else "deck.apkg").also {
            col.exportAnkiPackage(
                it.path,
                exportAnkiPackageOptions {
                    withScheduling = true
                    withDeckConfigs = true
                    withMedia = true
                    this.legacy = legacy
                },
                exportLimit { wholeCollection = Empty.getDefaultInstance() },
            )
        }
    }

    private fun provideFile(
        source: File,
        mimeType: String,
        displayName: String? = source.name,
        intentMimeType: String? = mimeType,
    ): Intent {
        val authority = "com.ichi2.anki.importtest"
        val provider = ImportProvider(source, mimeType, displayName) { filenameQueries++ }
        provider.attachInfo(targetContext, ProviderInfo().apply { this.authority = authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        // An opaque URI requires the import flow to use DISPLAY_NAME from the provider.
        return Intent(Intent.ACTION_VIEW).setDataAndType("content://$authority/document/123".toUri(), intentMimeType)
    }

    private fun assertCachedPackage(
        activity: DeckPicker,
        source: File,
    ) {
        val dialog = assertIs<ImportDialog>(activity.supportFragmentManager.fragments.last())
        val cachedPath = assertNotNull(dialog.requireArguments().getString(ImportDialog.ARG_PACKAGE_PATH))
        assertContentEquals(source.readBytes(), File(cachedPath).readBytes())
    }

    private class ImportProvider(
        private val source: File,
        private val mimeType: String,
        private val displayName: String?,
        private val onQuery: () -> Unit,
    ) : ContentProvider() {
        override fun onCreate() = true

        override fun getType(uri: Uri) = mimeType

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ) = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
            onQuery()
            addRow(arrayOf(displayName))
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ) = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = error("Not supported")

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Not supported")

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = error("Not supported")
    }
}
