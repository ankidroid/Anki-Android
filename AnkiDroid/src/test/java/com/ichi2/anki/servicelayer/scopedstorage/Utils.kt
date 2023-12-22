/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer.scopedstorage

import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.model.DiskFile
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.libanki.Media
import org.acra.util.IOUtils
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import java.io.File

/** Adds a media file to collection.media which [Media] is not aware of */
@CheckResult
internal fun addUntrackedMediaFile(
    media: Media,
    content: String,
    path: List<String>,
): DiskFile {
    val file = convertPathToMediaFile(media, path)
    File(file.parent!!).mkdirs()
    IOUtils.writeStringToFile(file, content)
    return DiskFile.createInstance(file)!!
}

private fun convertPathToMediaFile(
    media: Media,
    path: List<String>,
): File {
    val mutablePath = ArrayDeque(path)
    var file = File(media.dir)
    while (mutablePath.any()) {
        file = File(file, mutablePath.removeFirst())
    }
    return file
}

/** A [File] reference to the AnkiDroid directory of the current collection */
internal fun RobolectricTest.ankiDroidDirectory() = File(col.path).parentFile!!

internal fun RobolectricTest.setLegacyStorage() {
    getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, CollectionHelper.legacyAnkiDroidDirectory) }
}

/** Adds a file to collection.media which [Media] is not aware of */
@CheckResult
internal fun RobolectricTest.addUntrackedMediaFile(
    content: String,
    path: List<String>,
): DiskFile = addUntrackedMediaFile(col.media, content, path)

fun RobolectricTest.assertMigrationInProgress() {
    assertThat("the migration should be in progress", ScopedStorageService.mediaMigrationIsInProgress(this.targetContext), equalTo(true))
}

fun RobolectricTest.assertMigrationNotInProgress() {
    assertThat(
        "the migration should not be in progress",
        ScopedStorageService.mediaMigrationIsInProgress(this.targetContext),
        equalTo(false),
    )
}
