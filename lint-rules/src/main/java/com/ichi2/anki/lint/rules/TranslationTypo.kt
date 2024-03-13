/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element

/**
 * Ensures that strings are cased correctly
 *
 * `JavaScript`, not `Javascript`
 */
class TranslationTypo : ResourceXmlDetector(), XmlScanner {
    companion object {
        @VisibleForTesting
        val ID_TITLE_LENGTH = "TranslationTypo"

        @VisibleForTesting
        val EXPLANATION_TITLE_LENGTH = "Typo in translation"

        private val implementation = Implementation(TranslationTypo::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue = Issue.create(
            ID_TITLE_LENGTH,
            EXPLANATION_TITLE_LENGTH,
            EXPLANATION_TITLE_LENGTH,
            Constants.ANKI_XML_CATEGORY,
            Constants.ANKI_XML_PRIORITY,
            Constants.ANKI_XML_SEVERITY,
            implementation
        )
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES

    override fun visitElement(context: XmlContext, element: Element) {
        if ("preferences.xml" == context.file.name) {
            return
        }
        if ("resources" == element.tagName) {
            return
        }

        if (element.textContent.lowercase().contains("javascript") && !element.textContent.contains("JavaScript")) {
            context.report(ISSUE, context.getElementLocation(element), "should be 'JavaScript'")
        }
    }
}
