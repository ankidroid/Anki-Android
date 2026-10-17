// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class ExternalImageEditorResultTest : RobolectricTest(loadBackend = false) {
    private lateinit var editor: ExternalImageEditor
    private lateinit var resultProvider: EditorResultProvider
    private lateinit var cacheDirectory: File
    private lateinit var original: File
    private lateinit var copy: File

    @Before
    fun setUpEditor() {
        clearFileProviderRoots()
        cacheDirectory = File(targetContext.cacheDir, "external-editor").apply { mkdirs() }
        original = File(cacheDirectory, "original.png").apply { writeText("original") }
        copy = File(cacheDirectory, "working.png").apply { writeText("original") }
        editor = ExternalImageEditor(targetContext.applicationContext, cacheDirectory)
        resultProvider = EditorResultProvider.register()
    }

    @Test
    fun `a returned file uri is rejected`() =
        runBlocking {
            assertRejectedResult(Uri.fromFile(original))
        }

    @Test
    fun `an empty working copy is rejected`() =
        runBlocking {
            copy.writeText("")
            assertRejectedResult()
        }

    @Test
    fun `rejecting a same-name empty result preserves original bytes`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "empty.png").apply { writeText("") }
            assertRejectedResult(resultProvider.result(result, displayName = original.name))
        }

    @Test
    fun `returned display name cannot escape image cache`() =
        runBlocking {
            val existing = File(targetContext.filesDir, "existing.png").apply { writeText("untouched") }
            val result = File(targetContext.cacheDir, "edited.png").apply { writeText("editor bytes") }
            val accepted = assertNotNull(readResult(resultProvider.result(result, "../../files/existing.png")))
            assertEquals("untouched", existing.readText())
            assertEquals("editor bytes", accepted.readText())
            assertEquals(cacheDirectory, accepted.parentFile)
        }

    @Test
    fun `returned image keeps its mime type when the editor changes formats`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.jpg").apply { writeText("jpeg image") }
            val accepted = assertNotNull(readResult(resultProvider.result(result)))
            assertEquals("image/jpeg", targetContext.contentResolver.getType(uriForFile(accepted)))
            assertEquals("jpeg image", accepted.readText())
        }

    @Test
    fun `an unshared private cache file is rejected`() =
        runBlocking {
            val secret = File(targetContext.cacheDir, "private.png").apply { writeText("private data") }
            assertRejectedResult(uriForFile(secret))
            assertEquals("private data", secret.readText())
        }

    @Test
    fun `the supplied working uri is accepted`() =
        runBlocking {
            copy.writeText("modified")
            val accepted = assertNotNull(readResult(uriForFile(copy)))
            assertEquals("modified", accepted.readText())
            assertNotEquals(copy, accepted)
            assertEquals("original", original.readText())
        }

    @Test
    fun `external result is imported off the main thread`() =
        runBlocking {
            val result = File(targetContext.cacheDir, "edited.png").apply { writeText("edited") }
            val accepted = assertNotNull(readResult(resultProvider.result(result)))
            assertEquals("edited", accepted.readText())
            assertNotEquals(Looper.getMainLooper().thread, assertNotNull(resultProvider.openThread))
        }

    private suspend fun readResult(uri: Uri? = null): File? =
        editor.readResult(original, copy, ActivityResult(Activity.RESULT_OK, uri?.let { Intent().setData(it) }))

    private suspend fun assertRejectedResult(uri: Uri? = null) {
        assertFailsWith<IOException> { readResult(uri) }
        assertEquals("original", original.readText())
        assertEquals(setOf(original, copy), cacheDirectory.listFiles()!!.toSet())
    }

    private fun uriForFile(file: File): Uri =
        FileProvider.getUriForFile(targetContext, "${targetContext.packageName}.apkgfileprovider", file)
}
