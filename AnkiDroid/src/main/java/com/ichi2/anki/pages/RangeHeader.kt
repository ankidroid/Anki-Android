/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.pages

import fi.iki.elonen.NanoHTTPD.IHTTPSession
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels

/**
 * Handles the "range" header in a HTTP Request
 */
data class RangeHeader(val start: Long, val end: Long) {
    val contentLength: Long get() = end - start + 1

    companion object {
        fun from(
            session: IHTTPSession,
            defaultEnd: Long,
        ): RangeHeader? {
            val range = session.headers["range"]?.trim() ?: return null
            val numbers = range.substring("bytes=".length).split('-')
            val unspecifiedEnd = numbers.getOrNull(1).isNullOrEmpty()
            return RangeHeader(
                start = numbers[0].toLong(),
                end = if (unspecifiedEnd) defaultEnd else numbers[1].toLong(),
            )
        }
    }
}

fun File.toInputStream(header: RangeHeader): InputStream {
    // PERF: Test to see if a custom FileInputStream + available() would be faster
    val randomAccessFile = RandomAccessFile(this, "r")
    return Channels.newInputStream(randomAccessFile.channel).also {
        it.skip(header.start)
    }
}
