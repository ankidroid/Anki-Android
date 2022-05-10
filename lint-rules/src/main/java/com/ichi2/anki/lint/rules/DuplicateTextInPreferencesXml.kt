package com.ichi2.anki.lint.rules

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import org.w3c.dom.Element

/**
 * A Lint check to prevent using the same string for the title and summary of a preference.
 */
@KotlinCleanup("IDe lint")
class DuplicateTextInPreferencesXml : ResourceXmlDetector() {

    companion object {
        @VisibleForTesting
        val ID = "DuplicateTextInPreferencesXml"

        @VisibleForTesting
        val DESCRIPTION = "Do not use the same string for the title and summary of a preference"
        private const val EXPLANATION = "Use different strings for the title and summary of a preference to better " +
            "explain what that preference is for"
        private val implementation = Implementation(DuplicateTextInPreferencesXml::class.java, Scope.RESOURCE_FILE_SCOPE)
        @JvmField
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
