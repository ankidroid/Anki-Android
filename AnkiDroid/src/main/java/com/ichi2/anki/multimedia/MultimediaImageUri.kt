/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.InputStream

// Background:
// Android's content resolver throws SecurityException ("unsafe-content-uri-resolution")
// when asked to open a `file://` URI under /data/. When a share intent
// hands us an image we've already cached under our app's /data/.../cache: the URI is
// `file://...` but pointing at our own file. The two helpers below let callers read
// those URIs directly via java.io while still routing every other scheme (content://,
// etc.) through the caller's regular content-resolver path.

/**
 * Resolves [uri] to a local [File].
 *
 * Returns `null` when the URI has no path or [internalizer] yields `null`.
 */
fun resolveFileFromUri(
    uri: Uri,
    internalizer: (Uri) -> File?,
): File? =
    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.takeIf { it.isNotEmpty() }?.let(::File)
        else -> internalizer(uri)
    }

/**
 * Opens an [InputStream] for [uri].
 *
 * Returns `null` when the URI has no path or [fallback] yields `null`.
 */
fun openImageInputStream(
    uri: Uri,
    fallback: (Uri) -> InputStream?,
): InputStream? =
    when (uri.scheme) {
        ContentResolver.SCHEME_FILE -> uri.path?.takeIf { it.isNotEmpty() }?.let { File(it).inputStream() }
        else -> fallback(uri)
    }
