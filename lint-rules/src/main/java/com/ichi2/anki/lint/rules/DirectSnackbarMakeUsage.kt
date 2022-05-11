/*
 * Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>
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
import com.ichi2.anki.lint.utils.KotlinCleanup
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer uses the {com.google.android.material.snackbar.Snackbar#make(...)} method
 * instead of using the method provided by the UIUtils class {com.ichi2.anki.UIUtils#showSimpleSnackbar(...)}
 * or {com.ichi2.anki.UIUtils#showSnackbar(...)}.
 */
@KotlinCleanup("IDE lint")
@KotlinCleanup("mutableListOf")
class DirectSnackbarMakeUsage : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        @VisibleForTesting
        val ID = "DirectSnackbarMakeUsage"

        @JvmField
        @VisibleForTesting
        val DESCRIPTION = "Use UIUtils.showSimpleSnackbar or UIUtils.showSnackbar instead of Snackbar.make"
        private const val EXPLANATION = "To improve code consistency within the codebase you should use UIUtils.showSimpleSnackbar or UIUtils.showSnackbar " +
            "in place of the library Snackbar.make(...).show()"
        private val implementation = Implementation(DirectSnackbarMakeUsage::class.java, Scope.JAVA_FILE_SCOPE)
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
        forbiddenMethods.add("make")
        return forbiddenMethods
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "UIUtils") &&
            evaluator.isMemberInClass(method, "com.google.android.material.snackbar.Snackbar")
        ) {
            context.report(
                ISSUE,
                node,
                context.getCallLocation(node, true, true),
                DESCRIPTION
            )
        }
    }
}
