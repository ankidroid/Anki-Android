/*
 * Copyright (c) 2025 Nishtha Jain <jnishtha305@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Detector that ensures ContentResolver.openInputStream() is not called directly.
 * Instead, developers should use the openInputStreamSafe() extension function
 * which includes path traversal protection.
 */

class OpenInputStreamSafeDetector :
    Detector(),
    SourceCodeScanner {
    companion object {
        private const val EXPLANATION = """
            Use openInputStreamSafe() instead of openInputStream() to prevent \
            path traversal vulnerabilities. The safe version normalizes paths and blocks \
            access to sensitive directories like /data.
        """

        val ISSUE =
            Issue.create(
                id = "UnsafeOpenInputStream",
                briefDescription = "Use openInputStreamSafe() instead of openInputStream()",
                explanation = EXPLANATION,
                category = Category.SECURITY,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        OpenInputStreamSafeDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("openInputStream")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        // Only warn on ContentResolver.openInputStream()
        if (!context.evaluator.isMemberInClass(method, "android.content.ContentResolver")) return
        if (node.enclosingMethodName == "openInputStreamSafe") return
        context.report(
            issue = ISSUE,
            location = context.getNameLocation(node),
            message = "Use openInputStreamSafe() instead of openInputStream()",
        )
    }
}

val UCallExpression.enclosingMethodName: String
    get() = getParentOfType(UMethod::class.java)!!.name
