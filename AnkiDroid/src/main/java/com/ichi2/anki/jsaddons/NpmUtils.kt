/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
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

import androidx.annotation.CheckResult
import java.net.URLEncoder
import java.util.*

object NpmUtils {
    /**
     * Check if given string is a valid npm package name
     *
     * https://github.com/npm/validate-npm-package-name/blob/main/index.js
     * https://github.com/lassjs/is-valid-npm-name/blob/master/index.js
     */
    @CheckResult
    // @OptIn(ExperimentalContracts::class)
    fun validateName(name: String?): List<String>? {
        // Compiler fails on this contract.
        //        contract {
        //            returnsNotNull() implies (name != null)
        //        }
        if (name == null) {
            return listOf("Name is not set.")
        }
        val errors = mutableListOf<String>()
        for (invalidChar in "~'![{()}]* ") {
            if (name.contains(invalidChar)) errors.add("Name should not contains $invalidChar.")
        }
        if (name.lowercase(Locale.getDefault()) != name) errors.add("Name should be all lowercase.")
        if (name.length > 214) errors.add("Name's should be at most 214 character long.")

        val trimmedName = name.trim()
        val nameParts = trimmedName.split("/")
        // The names to check and the way to describe them in error message.
        val nameToChecks =
            when (nameParts.size) {
                1 -> {
                    listOf(Pair(trimmedName, "Name"))
                }
                2 -> {
                    val firstPart =
                        if (nameParts[0][0] != '@') {
                            errors.add("A scoped name should start with a @.")
                            nameParts[0]
                        } else {
                            nameParts[0].substring(1)
                        }
                    val secondPart = nameParts[1]
                    listOf(Pair(firstPart, "Name prefix"), Pair(secondPart, "Name suffix"))
                }
                else -> {
                    errors.add("Name should contains at most one /")
                    listOf(Pair(trimmedName, "Name"))
                }
            }
        for ((checkedNamePart, nameDescription) in nameToChecks) {
            val trimmedCheckedNamePart = checkedNamePart.trim()
            if (trimmedCheckedNamePart == "") errors.add("$nameDescription should contains non-whitespace symbols.")
            if (trimmedCheckedNamePart != checkedNamePart) errors.add("$nameDescription should not start nor ends with whitespace.")
            if (trimmedCheckedNamePart.startsWith(".")) errors.add("$nameDescription should not start with a dot.")
            if (trimmedCheckedNamePart.startsWith("_")) errors.add("$nameDescription should not start with an underscore.")
            if (URLEncoder.encode(trimmedCheckedNamePart, "UTF-8") != trimmedCheckedNamePart) errors.add("Name should be UTF-8 to be url-safe.")
        }

        return if (errors.isEmpty()) null else errors
    }
}
