/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     Copyright 2019 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *     https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/webkit/webkit/src/main/java/androidx/webkit/internal/AssetHelper.java#195
 *     at commit 1931e06ac0424ff5c7bbc2df34c658b9e95e11f6
 */
package com.ichi2.utils

import java.net.URLConnection

/** Clone of RestrictedApi functionality  */
object AssetHelper {
    /**
     * Use [URLConnection.guessContentTypeFromName] to guess MIME type or return the
     * "text/plain" if it can't guess.
     *
     * Copy of [androidx.webkit.internal.AssetHelper.guessMimeType]
     *
     * @param path path of the file to guess its MIME type.
     * @return MIME type guessed from file extension or "text/plain".
     */
    @JvmStatic
    fun guessMimeType(path: String?): String {
        val mimeType = URLConnection.guessContentTypeFromName(path)
        return mimeType ?: "text/plain"
    }
}
