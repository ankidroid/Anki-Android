/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * The result of an `ACTION_PICK` image chooser. The URI comes from whichever app the user picked,
 * so it's untrusted — a well-behaved picker returns a `content://`, and anything else (e.g. a
 * `file://`) is a bad or hostile picker we must not read.
 */
@JvmInline
value class PickedImage(
    private val intent: Intent,
) {
    /** The picked URI when it's a trusted `content://`, otherwise null. */
    val trustedUri: Uri?
        get() = intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}
