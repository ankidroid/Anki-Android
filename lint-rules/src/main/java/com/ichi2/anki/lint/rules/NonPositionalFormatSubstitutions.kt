/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.ALL_RESOURCES_SCOPE
import com.android.tools.lint.detector.api.XmlContext
import com.ichi2.anki.lint.utils.Aapt2Util
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.StringFormatDetector
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Fix for "Multiple substitutions specified in non-positional format"
 * [https://github.com/ankidroid/Anki-Android/issues/10347](https://github.com/ankidroid/Anki-Android/issues/10347)
 */
class NonPositionalFormatSubstitutions : ResourceXmlDetector() {
    companion object {
        private val IMPLEMENTATION_XML = Implementation(NonPositionalFormatSubstitutions::class.java, ALL_RESOURCES_SCOPE)

        /**
         * Whether there are any duplicate strings, including capitalization adjustments.
         */
        @JvmField
        val ISSUE = Issue.create(
            "NonPositionalFormatSubstitutions",
            "Multiple substitutions specified in non-positional format",
            "An XML string contains ambiguous format parameters " +
                "for example: %s %s. These should be positional (%1\$s %2\$s) to allow" +
                "translators to select the ordering.",
            Constants.ANKI_CROWDIN_CATEGORY,
            Constants.ANKI_CROWDIN_PRIORITY,
            Constants.ANKI_CROWDIN_SEVERITY,
            IMPLEMENTATION_XML
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements() = listOf(SdkConstants.TAG_STRING)

    override fun visitElement(context: XmlContext, element: Element) {
        // Check both the translated text and the "values" directory.

        val childNodes = element.childNodes
        if (childNodes.length <= 0) return

        if (childNodes.length == 1) {
            val child = childNodes.item(0)
            if (child.nodeType == Node.TEXT_NODE) {
                checkTextNode(
                    context,
                    element,
                    StringFormatDetector.stripQuotes(child.nodeValue)
                )
            }
        } else {
            val sb = StringBuilder()
            StringFormatDetector.addText(sb, element)
            if (sb.isNotEmpty()) {
                checkTextNode(context, element, sb.toString())
            }
        }
    }

    private fun checkTextNode(context: XmlContext, element: Element, text: String) {
        if (Aapt2Util.verifyJavaStringFormat(text)) return

        val location = context.createLocationHandle(element).resolve()

        // For clarity, the unescaped string is: "%s to %1$s"
        context.report(ISSUE, location, "Multiple substitutions specified in non-positional format. Convert \"%s\" to \"%1\$s\"")
    }
}
