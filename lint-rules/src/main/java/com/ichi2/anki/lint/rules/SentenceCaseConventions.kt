/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element

/**
 * Checks for issues in sentence-case.xml
 */
class SentenceCaseConventions :
    ResourceXmlDetector(),
    XmlScanner {
    companion object {
        @VisibleForTesting
        val ID = "SentenceCaseConventions"

        @VisibleForTesting
        val EXPLANATION = "Sentence-case style guide"

        private val implementation =
            Implementation(SentenceCaseConventions::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                EXPLANATION,
                EXPLANATION,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableElements(): Collection<String> = listOf("string")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        if (context.file.name != "sentence-case.xml") {
            return
        }

        // ensure 'name' starts with 'sentence_'
        // Convention: avoids reviewers needing to check strings against 'GeneratedTranslations'
        val elementName = element.getAttribute("name")
        if (!elementName.startsWith("sentence_")) {
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(element),
                message = "the 'name' attribute: '$elementName' should be prefixed with 'sentence_'",
            )
        }
    }
}
