/*
 * Copyright (c) 2021 Nicola Dardanis <nicdard@gmail.com>
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

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer uses the {android.widget.Toast#makeText(...)} method instead
 * of using the method provided by the UIUtils class {com.ichi2.anki.UIUtils#showThemedToast(...)}.
 */
class DirectToastMakeTextUsage : Detector(), SourceCodeScanner {

    companion object {
        @VisibleForTesting
        const val ID = "DirectToastMakeTextUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use UIUtils.showThemedToast instead of Toast.makeText"
        private const val EXPLANATION = "To improve code consistency within the codebase you should use UIUtils.showThemedToast in place" +
            " of the library Toast.makeText(...).show(). This ensures also that the toast is actually displayed after being created"
        private val implementation = Implementation(DirectToastMakeTextUsage::class.java, Scope.JAVA_FILE_SCOPE)
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

    override fun getApplicableMethodNames() = mutableListOf("makeText")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "UIUtils") && evaluator.isMemberInClass(method, "android.widget.Toast")) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                DESCRIPTION
            )
        }
    }
}
