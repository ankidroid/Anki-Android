// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import org.jetbrains.uast.UMethod
import java.util.EnumSet

class TestNameLengthDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (!context.isTestSource) return
                if (TEST_ANNOTATIONS.none { node.hasAnnotation(it) }) return
                val className = node.containingClass?.name ?: return

                val length = className.length + 1 + node.name.length
                if (length <= MAX_TEST_NAME_LENGTH) return

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Test name is $length characters (max $MAX_TEST_NAME_LENGTH): shorten the class or method name",
                )
            }
        }

    companion object {
        @VisibleForTesting
        const val MAX_TEST_NAME_LENGTH = 106

        private val TEST_ANNOTATIONS = listOf("org.junit.Test", "org.junit.jupiter.api.Test")

        @VisibleForTesting
        const val ID = "TestNameTooLong"

        @VisibleForTesting
        const val DESCRIPTION = "Test name is too long for Windows paths"

        private const val EXPLANATION =
            "Robolectric creates a temporary folder named after the test class and method. " +
                "On Windows, a long name pushes the collection path (and SQLite's `-journal` file) " +
                "past MAX_PATH, and the test fails, sometimes only intermittently. " +
                "Shorten the test method or class name. " +
                "See https://github.com/ankidroid/Anki-Android/issues/22163"

        private val implementation =
            Implementation(TestNameLengthDetector::class.java, EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES))

        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_CODE_STYLE_CATEGORY,
                Constants.ANKI_CODE_STYLE_PRIORITY,
                Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation,
            )
    }
}
