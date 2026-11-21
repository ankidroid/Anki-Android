// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.import_export.CsvMetadata
import anki.import_export.importCsvRequest
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class PageFragmentImportTest : RobolectricTest() {
    @Test
    fun `CSV import received after the importer stops still writes the notes`() =
        runTest {
            val csv = tempFolder.newFile("notes.csv").apply { writeText("front,back\n") }
            val input =
                importCsvRequest {
                    path = csv.path
                    metadata = col.getCsvMetadata(csv.path, CsvMetadata.Delimiter.COMMA)
                }.toByteArray()
            val controller =
                startActivityControllerNormallyOpenCollectionWithIntent(
                    SingleFragmentActivity::class.java,
                    CsvImporter.getIntent(targetContext, csv.path),
                )
            val page = assertIs<CsvImporter>(controller.get().fragment)
            assertEquals(0, col.noteCount())

            // The WebView's import POST can arrive after the activity stops.
            controller.pause().stop()
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            val request = async { page.handlePostRequest(PostRequestUri("/_anki/importCsv"), input) }
            advanceUntilIdle()
            val notesWhileStopped = col.noteCount()

            controller.restart().start().resume()
            request.await()
            assertEquals(1, col.noteCount(), "The CSV must contain one valid note")
            assertEquals(1, notesWhileStopped, "The import should finish while the activity is stopped")
        }
}
