// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.pages.PageFragment
import com.ichi2.anki.pages.PostRequestUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ImportSessionTest : RobolectricTest() {
    @Test
    fun `closing an import waits for its backend operation to return`() =
        runTest {
            val session = assertNotNull(ImportSession.acquire(targetContext))
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val operation =
                launch {
                    ImportSession.withOperation {
                        started.complete(Unit)
                        finish.await()
                    }
                }
            started.await()

            session.finish()

            assertNull(ImportSession.acquire(targetContext))
            finish.complete(Unit)
            operation.join()
            assertNotNull(ImportSession.acquire(targetContext)).finish()
        }

    @Test
    fun `late cleanup cannot release a newer import`() {
        val previous = assertNotNull(ImportSession.acquire(targetContext))
        previous.finish()
        val current = assertNotNull(ImportSession.acquire(targetContext))

        previous.finish()

        assertNull(ImportSession.acquire(targetContext))
        current.finish()
        assertNotNull(ImportSession.acquire(targetContext)).finish()
    }

    @Test
    fun `package importer holds ownership through recreation and results until closed`() =
        runTest {
            assertImportPageOwnsSession(AnkiPackageImporterFragment.getIntent(targetContext, "/unused.apkg"))
        }

    @Test
    fun `CSV importer holds ownership through recreation and results until closed`() =
        runTest {
            assertImportPageOwnsSession(CsvImporter.getIntent(targetContext, "/unused.csv"))
        }

    private suspend fun assertImportPageOwnsSession(intent: Intent) {
        val session = assertNotNull(ImportSession.acquire(targetContext))
        intent.putExtra(ImportSession.EXTRA_SESSION_ID, session.id.value)
        Robolectric.buildActivity(SingleFragmentActivity::class.java, intent).use { controller ->
            controller.setup().visible()
            advanceRobolectricLooper()
            assertFalse(controller.get().isFinishing)
            assertNull(ImportSession.acquire(targetContext))

            controller.recreate()
            advanceRobolectricLooper()
            assertFalse(controller.get().isFinishing)
            assertNull(ImportSession.acquire(targetContext))

            val page = controller.get().fragment as PageFragment
            page.handlePostRequest(PostRequestUri("/_anki/importDone"), byteArrayOf())
            assertNull(ImportSession.acquire(targetContext), "Results still own the import until dismissed")
        }
        assertNotNull(ImportSession.acquire(targetContext)).finish()
    }
}
