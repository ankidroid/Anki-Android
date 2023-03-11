/*
 *  Copyright (c) 2022 Divyansh Dwivedi <justdvnsh2208@gmail.com>
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

import com.android.SdkConstants.*
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.ALL_RESOURCES_SCOPE
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.forEach
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_CATEGORY
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_PRIORITY
import com.ichi2.anki.lint.utils.Constants.ANKI_XML_SEVERITY
import com.ichi2.anki.lint.utils.StringFormatDetector
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.*
import java.util.regex.Pattern

/**
 * Fix for "Linting Error - String format should be valid."
 * [https://github.com/ankidroid/Anki-Android/issues/10604](https://github.com/ankidroid/Anki-Android/issues/10604)
 */
class InvalidStringFormatDetector : ResourceXmlDetector() {
    companion object {
        private val IMPLEMENTATION_XML =
            Implementation(InvalidStringFormatDetector::class.java, ALL_RESOURCES_SCOPE)

        /**
         * Whether the string or plural resource that is being used has all the translations
         * **/
        val ISSUE = Issue.create(
            "InvalidStringFormat",
            "The String format is invalid",
            "The String format used is invalid, Make sure to use a valid string format",
            ANKI_XML_CATEGORY,
            ANKI_XML_PRIORITY,
            ANKI_XML_SEVERITY,
            IMPLEMENTATION_XML
        )

        private val INVALID_FORMAT_PATTERN = Pattern.compile("[^%]+%").toRegex()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        EnumSet.of(ResourceFolderType.VALUES).contains(folderType)

    override fun getApplicableElements() = listOf(TAG_STRING, TAG_PLURALS)

    override fun visitElement(context: XmlContext, element: Element) {
        val childNodes = element.childNodes
        if (childNodes.length <= 0) return

        element.childNodes
            .forEach { child ->
                val isStringResource = (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) &&
                    TAG_STRING == element.localName
                val isStringArrayOrPlurals = child.nodeType == Node.ELEMENT_NODE &&
                    (
                        TAG_STRING_ARRAY == element.localName ||
                            TAG_PLURALS == element.localName
                        )

                if (isStringResource) {
                    checkText(context, element, child.nodeValue)
                } else if (isStringArrayOrPlurals) {
                    val sb = StringBuilder()
                    StringFormatDetector.addText(sb, element)
                    if (sb.isNotEmpty()) {
                        checkText(context, element, sb.toString())
                    }
                }
            }
    }

    private fun checkText(context: XmlContext, element: Element, text: String) {
        text.split(" ").forEach {
            if (it.matches(INVALID_FORMAT_PATTERN) && it != "XXX%") {
                val location = context.createLocationHandle(element).resolve()
                context.report(
                    ISSUE,
                    location,
                    "You have specified the string in wrong format" +
                        "Please check that '%' sign been applied only to valid parameters. " +
                        "Your string might be having a regular word with '%' sign after it. " +
                        "eg: 'I have completed% %s cards.' "
                )
            }
        }
    }
}
