/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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

@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element

/**
 * A Lint check to prevent using the same string for the title and summary of a preference.
 */
class DuplicateTextInPreferencesXml : ResourceXmlDetector() {

    companion object {
        @VisibleForTesting
        val ID = "DuplicateTextInPreferencesXml"

        @VisibleForTesting
        val DESCRIPTION = "Do not use the same string for the title and summary of a preference"
        private const val EXPLANATION = "Use different strings for the title and summary of a preference to better " +
            "explain what that preference is for"
        private val implementation = Implementation(DuplicateTextInPreferencesXml::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
        )
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_SUMMARY = "android:summary"
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.hasAttribute(ATTR_TITLE) && element.hasAttribute(ATTR_SUMMARY)) {
            val titleValue = element.getAttribute(ATTR_TITLE)
            val summaryValue = element.getAttribute(ATTR_SUMMARY)
            if (titleValue == summaryValue) {
                context.report(ISSUE, element, context.getLocation(element), DESCRIPTION)
            }
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }
}
