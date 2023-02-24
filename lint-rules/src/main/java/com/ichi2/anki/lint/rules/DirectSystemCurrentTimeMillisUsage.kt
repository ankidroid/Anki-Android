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

/**
 * This custom Lint rules will raise an error if a developer uses the [System.currentTimeMillis] method instead
 * of using the time provided by the new Time class.
 */
class DirectSystemCurrentTimeMillisUsage : Detector(), SourceCodeScanner {

    companion object {
        @VisibleForTesting
        const val ID = "DirectSystemCurrentTimeMillisUsage"

        @VisibleForTesting
        const val DESCRIPTION = "Use the collection's getTime() method instead of System.currentTimeMillis()"
        private const val EXPLANATION = "Using time directly means time values cannot be controlled during testing. " +
            "Time values like System.currentTimeMillis() must be obtained through the Time obtained from a Collection"
        private val implementation = Implementation(DirectSystemCurrentTimeMillisUsage::class.java, Scope.JAVA_FILE_SCOPE)
        val ISSUE: Issue = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
        )
    }

    override fun getApplicableMethodNames() = mutableListOf("currentTimeMillis")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "SystemTime") && evaluator.isMemberInClass(method, "java.lang.System")) {
            context.report(
                ISSUE,
                context.getCallLocation(node, includeReceiver = true, includeArguments = true),
                DESCRIPTION
            )
        }
    }
}
