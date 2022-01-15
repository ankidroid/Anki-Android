/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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

package com.ichi2.anki.jsaddons

import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

object NpmUtils {
    // Update if api get updated
    const val ANKIDROID_JS_API = "0.0.1"
    const val ANKIDROID_JS_ADDON_KEYWORDS = "ankidroid-js-addon"
    const val REVIEWER_ADDON = "reviewer"
    const val NOTE_EDITOR_ADDON = "note-editor"
}

// https://github.com/npm/validate-npm-package-name/blob/main/index.js
// https://github.com/lassjs/is-valid-npm-name/blob/master/index.js
fun validateName(name: String): Boolean {
    if (name.isNullOrBlank()) {
        return false
    }

    // first trim it
    if (name.trim() != name) {
        return false
    }

    // name can no longer contain more than 214 characters
    if (name.length > 214) {
        return false
    }

    // name cannot start with a period or an underscore
    if (name.startsWith(".") || name.startsWith("_")) {
        return false
    }

    // name can no longer contain capital letters
    if (name.lowercase(Locale.getDefault()) != name) {
        return false
    }

    // must have @
    // must have @ at beginning of string
    if (name.startsWith("@")) {
        // must have only one @
        if (name.indexOf('@') != name.lastIndexOf('@')) {
            return false
        }

        // must have /
        if (!name.contains('/')) {
            return false
        }

        // must have only one /
        if (name.indexOf('/') != name.lastIndexOf('/')) {
            return false
        }

        // validate scope
        val arr = name.split('/')
        val scope = arr[0].removePrefix("@")
        val isValidScopeName = validateName(scope)

        if (!isValidScopeName) {
            return isValidScopeName
        }

        // validate name again
        return validateName(arr[1])
    }

    // no non-URL-safe characters
    if (URLEncoder.encode(name, "UTF-8") != name) {
        return false
    }

    // name can no longer contain special characters ("~\'!()*")'
    val special = Pattern.compile("[~\'!()*]")
    if (special.matcher(name).find()) {
        return false
    }

    return true
}
