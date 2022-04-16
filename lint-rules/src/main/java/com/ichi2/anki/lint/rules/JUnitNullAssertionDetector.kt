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

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer uses JUnit's `assert(Not)Null`
 * methods instead of using the methods from `kotlin-test`
 *
 * `kotlin-test` methods use contracts, reducing the need for `!!` in the code
 *
 * TODO/PERF: Only run this in tests, there's no JUnit in /src/
 */
class JUnitNullAssertionDetector : Detector(), SourceCodeScanner {
    /** Detect both assertNotNull and assertNull: assertNotNull is the most likely to cause improvements */
    override fun getApplicableMethodNames(): List<String> = arrayListOf("assertNotNull", "assertNull")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        // only for kotlin files
        if (!context.file.path.endsWith(".kt")) return
        // only for org.junit.Assert.assert[Not]Null
        if (!context.evaluator.isMemberInClass(method, "org.junit.Assert")) return

        context.report(
            ISSUE,
            context.getCallLocation(node, includeReceiver = true, includeArguments = true),
            DESCRIPTION
        )
    }

    companion object {
        @VisibleForTesting
        val ID = "LegacyNullAssertionDetector"

        @VisibleForTesting
        val DESCRIPTION = "Use kotlin.test.assert[Not]Null OR kotlin.test.junit.JUnitAsserter instead of JUnit in Kotlin"
        private const val EXPLANATION = "JUnitAsserter's methods use contracts, removing the need for `!!` " +
            "afterwards. Use JUnitAsserter if passing in a message, kotlin.test top level functions otherwise"
        private val implementation = Implementation(JUnitNullAssertionDetector::class.java, JAVA_FILE_SCOPE)

        @JvmField
        val ISSUE: Issue = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
        )
    }
}
