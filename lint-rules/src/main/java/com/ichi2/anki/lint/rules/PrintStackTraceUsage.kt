/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

@KotlinCleanup("IDE Lint")
@KotlinCleanup("mutableListOf")
class PrintStackTraceUsage : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        @VisibleForTesting
        val ID = "PrintStackTraceUsage"

        @VisibleForTesting
        val DESCRIPTION = "Use Timber to log exceptions (typically Timber.w if non-fatal)"
        private const val EXPLANATION = "AnkiDroid exclusively uses Timber for logging exceptions. See: https://github.com/ankidroid/Anki-Android/wiki/Code-style#logging"
        private val implementation = Implementation(PrintStackTraceUsage::class.java, Scope.JAVA_FILE_SCOPE)
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

    override fun getApplicableMethodNames(): List<String>? {
        val forbiddenMethods: MutableList<String> = ArrayList()
        forbiddenMethods.add("printStackTrace")
        return forbiddenMethods
    }

    @KotlinCleanup("remove comment about semicolon")
    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator

        // if we have arguments, we're not writing to stdout, so it's an OK call
        val hasArguments = node.valueArgumentCount != 0
        if (hasArguments || !evaluator.isMemberInSubClassOf(method, "java.lang.Throwable", false)) {
            return
        }
        val fix = LintFix.create()
            .replace()
            .select(node.asSourceString())
            // We don't need a semicolon here
            .with("Timber.w(" + node.receiver!!.asSourceString() + ")")
            .autoFix()
            .build()
        context.report(
            ISSUE,
            context.getCallLocation(node, true, true),
            DESCRIPTION,
            fix
        )
    }
}
