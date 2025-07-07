/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.test.assertNotEquals

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerViewModelFileSystemTest : RobolectricTest() {
    // TODO(PERF): Needs investigation why this is necessary
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    fun `card ords are changed`() {
        runClozeTest(tempDirectory = tempDirectory, fields = mutableListOf("{{c1::one}} {{c2::bar}}")) {
            onPageFinished(false)
            val ord1 = currentCard.await().ord
            onTabSelected(1)
            val ord2 = currentCard.await().ord
            assertNotEquals(ord1, ord2)
        }
    }
}
