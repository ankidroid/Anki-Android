/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.Beta
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.jetbrains.uast.UClass
import java.util.*
import java.util.regex.Pattern

@Beta
class KotlinMigrationFixLineBreaks : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun afterCheckFile(context: Context) {
        val contents = context.getContents()
        if (contents == null || !BAD_KOTLIN_MIGRATION_PATTERN.matcher(contents).find()) {
            return
        }

        // select from the start to the first line with content
        var end = 0
        var foundChar = false
        for (i in contents.indices) {
            foundChar = foundChar or !Character.isWhitespace(contents[i])
            if (foundChar && contents[i] == '\n') {
                end = i
                break
            }
        }

        // If there is no line break, highlight the contents
        // TODO: Improve the 'Location' code to be usable fot entire file, not just copyright header
        val endOffset = if (end == 0) contents.length else end
        val location: Location = Location.create(context.file, contents.subSequence(0, endOffset), 0, endOffset)
        context.report(ISSUE, location, DESCRIPTION)
    }

    companion object {
        /** Java -> Kotlin converts <br/> to <br></br> */
        private val BAD_KOTLIN_MIGRATION_PATTERN = Pattern.compile(Pattern.quote("<br></br>"))

        @VisibleForTesting
        val ID = "KotlinMigrationFixLineBreaks"

        @VisibleForTesting
        val DESCRIPTION = "Tags were corrupted by the Kotlin migration. For example: <br></br>"
        private const val EXPLANATION = "<br></br> was detected in the file.\n" +
            "Please check the relevant piece of code.\n" +
            "And change instances of <br></br> to a newline: Kotlin supports Markdown formatting."
        private val implementation = Implementation(KotlinMigrationFixLineBreaks::class.java, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES))
        val ISSUE: Issue = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
        )
    }
}
