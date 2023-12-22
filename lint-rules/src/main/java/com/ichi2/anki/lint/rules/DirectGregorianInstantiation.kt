/****************************************************************************************
 * Copyright (c) 2020 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
@file:Suppress("UnstableApiUsage")

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import java.util.GregorianCalendar

/**
 * This custom Lint rules will raise an error if a developer creates [GregorianCalendar] instances directly
 * instead of using the collection's getTime() method.
 */
class DirectGregorianInstantiation : Detector(), SourceCodeScanner {
    companion object {
        @VisibleForTesting
        const val ID = "DirectGregorianInstantiation"

        @VisibleForTesting
        const val DESCRIPTION = "Use the collection's getTime() method instead of directly creating GregorianCalendar instances"
        private const val EXPLANATION =
            "Creating GregorianCalendar instances directly is not allowed, as it " +
                "prevents control of time during testing. Use the collection's getTime() method instead"
        private val implementation = Implementation(DirectGregorianInstantiation::class.java, Scope.JAVA_FILE_SCOPE)
        val ISSUE: Issue =
            Issue.create(
                ID,
                DESCRIPTION,
                EXPLANATION,
                Constants.ANKI_TIME_CATEGORY,
                Constants.ANKI_TIME_PRIORITY,
                Constants.ANKI_TIME_SEVERITY,
                implementation,
            )
    }

    override fun getApplicableMethodNames() = mutableListOf("from")

    override fun getApplicableConstructorTypes() = mutableListOf("java.util.GregorianCalendar")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time") && evaluator.isMemberInClass(method, "java.util.GregorianCalendar")) {
            context.report(
                ISSUE,
                context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                DESCRIPTION,
            )
        }
    }

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod,
    ) {
        super.visitConstructor(context, node, constructor)
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION,
            )
        }
    }
}
