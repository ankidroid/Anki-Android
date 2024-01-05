/*
 *  Copyright (c) 2021 Almas Ahmad <ahmadalmas.786.aa@gmail.com>
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
import org.jetbrains.uast.UElement
import java.util.*
import java.util.regex.Pattern

/**
 * Ensures that a GPLv3-compatible copyright header exists in all files.
 *
 * Provides instructions and documentation for a long-term fix if this is triggered.
 *
 * @see .EXPLANATION
 */
@Beta
class CopyrightHeaderExists : Detector(), SourceCodeScanner {

    companion object {
        /** This string matches GPLv3 under all current circumstances. It does not currently work if split over two lines  */
        private val COPYRIGHT_PATTERN = Pattern.compile("version 3 of the License, or \\(at")

        /**
         * &#64;SuppressWarnings doesn't work as it's the first statement, so allow suppression via:
         * `//noinspection MissingCopyrightHeader <reason>`
         */
        private val IGNORE_CHECK_PATTERN = Pattern.compile("MissingCopyrightHeader")

        @VisibleForTesting
        const val ID = "MissingCopyrightHeader"

        @VisibleForTesting
        const val DESCRIPTION = "All files in AnkiDroid must contain a GPLv3-compatible copyright header"
        private const val EXPLANATION = "All files in AnkiDroid must contain a " +
            "GPLv3-compatible copyright header" +
            "The copyright header can be set in " +
            "Settings - Editor - Copyright - Copyright Profiles - Add Profile - AnkiDroid. " +
            "Or search in Settings for 'Copyright'" +
            "A GPLv3 template is available:\n" +
            "https://github.com/ankidroid/Anki-Android/issues/8211#issuecomment-825269673 \n\n" +
            "If the file is under a GPL-Compatible License (https://www.gnu.org/licenses/license-list.en.html#GPLCompatibleLicenses) " +
            "then this warning may be suppressed either via adding a GPL header added alongside the license: " +
            "https://softwarefreedom.org/resources/2007/gpl-non-gpl-collaboration.html#x1-40002.2 + or " +
            "\"//noinspection MissingCopyrightHeader <reason>\" may be added as the first line of the file."
        private val implementation = Implementation(CopyrightHeaderExists::class.java, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES))
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

    override fun getApplicableUastTypes(): List<Class<out UElement?>> {
        return listOf(UClass::class.java)
    }

    override fun afterCheckFile(context: Context) {
        val contents = context.getContents()
        if (contents == null || COPYRIGHT_PATTERN.matcher(contents).find() ||
            IGNORE_CHECK_PATTERN.matcher(contents).find()
        ) {
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
        val endOffset = if (end == 0) contents.length else end
        val location: Location = Location.create(context.file, contents.subSequence(0, endOffset), 0, endOffset)
        context.report(ISSUE, location, DESCRIPTION)
    }
}
