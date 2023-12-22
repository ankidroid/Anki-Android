/*
 *  Copyright (c) 2021 Prateek Singh <prateeksingh3212@gmail.com>
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
import com.android.tools.lint.detector.api.Location.Handle
import com.android.utils.Pair
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.w3c.dom.Element
import java.util.*

class FixedPreferencesTitleLength : ResourceXmlDetector(), XmlScanner {
    companion object {
        @VisibleForTesting
        val ID_TITLE_LENGTH = "FixedPreferencesTitleLength"

        @VisibleForTesting
        val ID_MAX_LENGTH = "PreferencesTitleMaxLengthAttr"
        private const val PREFERENCE_TITLE_MAX_LENGTH = 41

        @VisibleForTesting
        val DESCRIPTION_TITLE_LENGTH = "Preference titles should be less than $PREFERENCE_TITLE_MAX_LENGTH characters"

        @VisibleForTesting
        val DESCRIPTION_MAX_LENGTH = """Preference titles should contain maxLength="$PREFERENCE_TITLE_MAX_LENGTH" attribute"""

        // Around 42 is a hard max on emulators, likely smaller in reality, so use a buffer
        private const val EXPLANATION_TITLE_LENGTH =
            "A title with more than $PREFERENCE_TITLE_MAX_LENGTH characters may fail to display on smaller screens"

        // Read More: https://support.crowdin.com/file-formats/android-xml/
        private const val EXPLANATION_MAX_LENGTH =
            "Preference Title should contain maxLength attribute " +
                "because it fixes translated string length"
        private val implementation = Implementation(FixedPreferencesTitleLength::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE_TITLE_LENGTH: Issue =
            Issue.create(
                ID_TITLE_LENGTH,
                DESCRIPTION_TITLE_LENGTH,
                EXPLANATION_TITLE_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
        val ISSUE_MAX_LENGTH: Issue =
            Issue.create(
                ID_MAX_LENGTH,
                DESCRIPTION_MAX_LENGTH,
                EXPLANATION_MAX_LENGTH,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
        private const val ATTR_TITLE = "android:title"
        private const val ATTR_NAME = "name"
        private const val ATTR_MAX_LENGTH = "maxLength"
    }

    private val xmlData: MutableSet<String> = HashSet()
    private val valuesData: MutableMap<String, Pair<Element, Handle>> = HashMap<String, Pair<Element, Handle>>()

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        /* 1. This condition checks that current file has xml as a parent file.
           2. Checks that element contains title attribute or not.
           If both of conditions are true then set the value in xmlSet.
         */
        if ("xml" == context.file.parentFile.name && element.hasAttribute(ATTR_TITLE)) {
            val stringName = element.getAttribute(ATTR_TITLE).substring(8)
            xmlData.add(stringName)
            return
        }
        if ("values" != context.file.parentFile.name) {
            return
        }
        if ("10-preferences.xml" != context.file.name) {
            return
        }
        if ("resources" == element.tagName) {
            return
        }
        val handle: Handle = context.createLocationHandle(element)
        handle.clientData = element
        valuesData[element.getAttribute(ATTR_NAME)] = Pair.of(element, handle)
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML || folderType == ResourceFolderType.VALUES
    }

    override fun afterCheckEachProject(context: Context) {
        for (title in xmlData) {
            if (!valuesData.containsKey(title)) {
                continue
            }
            val stringData: Pair<Element, Handle> = valuesData[title]!!
            val element = stringData.first
            if (!element.hasAttribute(ATTR_MAX_LENGTH)) {
                val message =
                    String.format(
                        Locale.ENGLISH,
                        "Preference title '%s' is missing \"maxLength=%d\" attribute",
                        title,
                        PREFERENCE_TITLE_MAX_LENGTH,
                    )
                context.report(ISSUE_MAX_LENGTH, stringData.second.resolve(), message)
            } else if (element.getAttribute(ATTR_MAX_LENGTH) != PREFERENCE_TITLE_MAX_LENGTH.toString()) {
                val message =
                    String.format(
                        Locale.ENGLISH,
                        "Preference title '%s' is having maxLength=%s it should contain maxLength=%d",
                        title,
                        element.getAttribute(ATTR_MAX_LENGTH),
                        PREFERENCE_TITLE_MAX_LENGTH,
                    )
                context.report(ISSUE_MAX_LENGTH, stringData.second.resolve(), message)
            }
            if (element.textContent.length > PREFERENCE_TITLE_MAX_LENGTH) {
                val message =
                    String.format(
                        Locale.ENGLISH,
                        "Preference title '%s' must be less than %d characters (currently %d)",
                        title,
                        PREFERENCE_TITLE_MAX_LENGTH,
                        element.textContent.length,
                    )
                context.report(ISSUE_TITLE_LENGTH, stringData.second.resolve(), message)
            }
        }
    }
}
