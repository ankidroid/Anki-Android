/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
 * A Lint check to prevent using hardcoded strings on preferences keys
 */
class HardcodedPreferenceKey : ResourceXmlDetector() {
    companion object {
        @VisibleForTesting
        val ID = "HardcodedPreferenceKey"

        @VisibleForTesting
        val DESCRIPTION = "Preference key should not be hardcoded"
        private const val EXPLANATION = "Extract the key to a resources XML so it can be reused"
        private val implementation = Implementation(HardcodedPreferenceKey::class.java, Scope.RESOURCE_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_XML_CATEGORY,
                Constants.ANKI_XML_PRIORITY,
                Constants.ANKI_XML_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableElements(): Collection<String>? {
        return ALL
    }

    override fun visitElement(
        context: XmlContext,
        element: Element,
    ) {
        reportAttributeIfHardcoded(context, element, "android:key")
        reportAttributeIfHardcoded(context, element, "android:dependency")
    }

    private fun reportAttributeIfHardcoded(
        context: XmlContext,
        element: Element,
        attributeName: String,
    ) {
        val attrNode = element.getAttributeNode(attributeName) ?: return

        if (isHardcodedString(attrNode.value)) {
            context.report(ISSUE, element, context.getLocation(attrNode), DESCRIPTION)
        }
    }

    private fun isHardcodedString(string: String): Boolean {
        // resources start with a `@`, and attributes start with a `?`
        return string.isNotEmpty() && !string.startsWith("@") && !string.startsWith("?")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.XML
    }
}
