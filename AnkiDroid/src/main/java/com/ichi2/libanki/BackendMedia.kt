/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki

class BackendMedia(val col: CollectionV16, server: Boolean) : Media(col, server) {
    override fun connect() {
        // no-op
    }

    override fun close() {
        // no-op
    }

    override fun rebuildIfInvalid() {
        // no-op
    }

    override fun findChanges(force: Boolean) {
        // no-op
    }

    override fun markFileAdd(fname: String) {
        // no-op; will no longer be called when migrating to new import code.
    }

    override fun forceResync() {
        col.backend.removeMediaDb(colPath = col.path)
    }

    override fun removeFile(fname: String) {
        removeFiles(listOf(fname))
    }

    // markFileAdd

    // FIXME: this also provides trash count, but UI can not handle it yet
    override fun check(): MediaCheckResult {
        val out = col.backend.checkMedia()
        return MediaCheckResult(out.missingList, out.unusedList, listOf())
    }

    // FIXME: this currently removes files immediately, as the UI does not expose a way
    // to empty the trash or restore media files yet
    fun removeFiles(files: Iterable<String>) {
        col.backend.trashMediaFiles(fnames = files)
        emptyTrash()
    }

    private fun emptyTrash() {
        col.backend.emptyTrash()
    }

    @Suppress("UNUSED")
    private fun restoreTrash() {
        col.backend.restoreTrash()
    }
}
