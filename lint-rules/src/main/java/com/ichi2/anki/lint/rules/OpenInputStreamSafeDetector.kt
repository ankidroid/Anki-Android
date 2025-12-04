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
        // Check if this is ContentResolver.openInputStream()
        // The evaluator checks if this method is a member of "android.content.ContentResolver".
        // If it's not (e.g., it's some custom class's openInputStream), we ignore it and return early.
        val evaluator = context.evaluator
        if (!evaluator.isMemberInClass(method, "android.content.ContentResolver")) {
            return
        }

        context.report(
            issue = ISSUE,
            location = context.getNameLocation(node),
            message = "Use openInputStreamSafe() instead of openInputStream() for security",
        )
    }
}
