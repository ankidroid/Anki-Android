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
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.CrowdinContext.Companion.toCrowdinContext
import com.ichi2.anki.lint.utils.ext.isRightToLeftLanguage
import org.w3c.dom.Element

/**
 * Checks for a variety of errors commonly made in CrowdIn
 *
 * `JavaScript`, not `Javascript`, ellipses, empty strings, etc
 */
class TranslationTypo : ResourceXmlDetector(), XmlScanner {
    companion object {
        @VisibleForTesting
        val ID_TRANSLATION_TYPO = "TranslationTypo"

        @VisibleForTesting
        val EXPLANATION_TRANSLATION_TYPO = "Typo in translation"

        private val implementation = Implementation(TranslationTypo::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue = Issue.create(
            ID_TRANSLATION_TYPO,
            EXPLANATION_TRANSLATION_TYPO,
            EXPLANATION_TRANSLATION_TYPO,
            Constants.ANKI_XML_CATEGORY,
            Constants.ANKI_XML_PRIORITY,
            Constants.ANKI_XML_SEVERITY,
            implementation
        )

        // copied from tools/localization/src/constants.ts
        // excludes marketdescription as it is .txt
        private val I18N_FILES = listOf(
            "01-core",
            "02-strings",
            "03-dialogs",
            "04-network",
            "05-feedback",
            "06-statistics",
            "07-cardbrowser",
            "08-widget",
            "09-backup",
            "10-preferences",
            "11-arrays",
            "16-multimedia-editor",
            "17-model-manager",
            "18-standard-models",
            "20-search-preference"
        ).map { "$it.xml" }

        // CrowdIn strings+ additional string XML which are not translated
        val STRING_XML_FILES = I18N_FILES + listOf("constants.xml", "sentence-case.xml")
    }

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES

    override fun visitElement(context: XmlContext, element: Element) {
        // ignore files not containing strings
        if (!STRING_XML_FILES.contains(context.file.name)) {
            return
        }

        // Only check <string> or <plurals><item>, not the container
        if ("resources" == element.tagName) {
            return
        }

        val crowdinContext = context.toCrowdinContext()

        /** Helper function to report 'TranslationTypo' issues */
        fun Element.reportIssue(message: String) {
            val elementToReport = this
            val crowdinEditUrl = crowdinContext?.getEditUrl(elementToReport)
                ?.let { url -> "\n$url" } ?: ""
            context.report(
                issue = ISSUE,
                location = context.getElementLocation(elementToReport),
                message = message + crowdinEditUrl
            )
        }

        // use the unicode character instead of three dots for ellipsis
        // ignore RTL to reduce maintenance: GitHub incorrectly displays ellipsis, so hard to review
        if (element.textContent.contains("...") && !context.isRightToLeftLanguage()) {
            element.reportIssue("should use unicode '&#8230;' for ellipsis not three dots; RTL languages best viewed on crowdin")
        }

        // casing of 'JavaScript'
        if (element.textContent.lowercase().contains("javascript") && !element.textContent.contains("JavaScript")) {
            element.reportIssue("should be 'JavaScript'")
        }

        // remove empty strings
        if (element.textContent.isEmpty() && element.getAttribute("name") != "empty_string") {
            element.reportIssue("should not be empty")
        }
    }
}
