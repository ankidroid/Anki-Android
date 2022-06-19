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
import com.ichi2.anki.lint.utils.LintUtils.isAnAllowedClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer instantiates the SystemTime class directly
 * instead of using the Time class from a Collection.
 *
 * NOTE: For future reference, if you plan on creating a Lint rule which looks for a constructor invocation, make sure
 * that the target class has a constructor defined in its source code!
 */
class DirectSystemTimeInstantiation : Detector(), SourceCodeScanner {

    companion object {
        @VisibleForTesting
        const val ID = "DirectSystemTimeInstantiation"

        @VisibleForTesting
        const val DESCRIPTION =
            "Use the collection's getTime() method instead of instantiating SystemTime"
        private const val EXPLANATION =
            "Creating SystemTime instances directly means time cannot be controlled during" +
                " testing, so it is not allowed. Use the collection's getTime() method instead"
        private val implementation = Implementation(
            DirectSystemTimeInstantiation::class.java,
            Scope.JAVA_FILE_SCOPE
        )
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

    override fun getApplicableConstructorTypes() = listOf("com.ichi2.libanki.utils.SystemTime")

    override fun visitConstructor(
        context: JavaContext,
        node: UCallExpression,
        constructor: PsiMethod
    ) {
        super.visitConstructor(context, node, constructor)
        val foundClasses = context.uastFile!!.classes
        if (!isAnAllowedClass(foundClasses, "Storage", "CollectionHelper")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION
            )
        }
    }
}
