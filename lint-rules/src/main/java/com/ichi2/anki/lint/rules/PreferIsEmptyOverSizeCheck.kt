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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Detector.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*
import java.util.*
import java.util.regex.Pattern

@KotlinCleanup("remove after all converted")
class PreferIsEmptyOverSizeCheck : Detector(), UastScanner {

    companion object {
        @VisibleForTesting
        val ID = "PreferIsEmptyOverSize"

        @VisibleForTesting
        val DESCRIPTION = "Always prefer `isEmpty` instead of size comparison when possible"
        private const val EXPLANATION = "To improve code consistency within the codebase you should use `isEmpty` when possible in place" +
            " of checking that the size is zero."
        private val implementation = Implementation(PreferIsEmptyOverSizeCheck::class.java, Scope.JAVA_FILE_SCOPE)
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
        private val pattern = Pattern.compile("\\.size\\(\\)([^=]*)(==|>|<|>=|<=).*\\z")
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return BinaryExpressionHandler(context)
    }

    override fun getApplicableUastTypes() =
        mutableListOf(UBinaryExpression::class.java, UCallExpression::class.java)

    /**
     * Report the problematic comparison expression to the lint checker and compute the fix to apply
     * since the pattern().with() seems to behave strangely.
     */
    fun reportVariable(
        context: JavaContext,
        node: UBinaryExpression,
        isAffirmative: Boolean
    ) {
        val source = node.asSourceString()
        val matcher = pattern.matcher(source)
        var fixedSource = matcher.replaceFirst(".isEmpty()$1").trim { it <= ' ' }
        if (!isAffirmative) {
            fixedSource = String.format("!%s", fixedSource)
        }
        val fix = LintFix.create()
            .replace()
            .range(context.getLocation(node))
            .text(source)
            .with(fixedSource)
            .reformat(true)
            .build()
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            DESCRIPTION,
            fix
        )
    }

    private inner class BinaryExpressionHandler(private val mJavaContext: JavaContext) : UElementHandler() {
        private var parentNode: UBinaryExpression? = null
        private var isAffirmative: Boolean? = null
        override fun visitCallExpression(node: UCallExpression) {
            // Needed to check if the class has an `isEmpty` method.
            if (parentNode == null || isAffirmative == null || "size" != node.methodName) {
                return
            }
            // Check that this is a child of the parent we have already seen.
            var n: UElement? = node
            while (n != null && n !== parentNode) {
                n = n.uastParent
            }
            if (n == null) {
                // Optimisation: since the current node is not in the subtree of parentNode we are done
                // searching for this parentNode (we have already visited all the subtree and exited from it)
                parentNode = null
                isAffirmative = null
                return
            }
            val methods = mJavaContext.evaluator.getTypeClass(node.receiverType)!!.allMethods
            if (Arrays.stream(methods).anyMatch { m: PsiMethod -> "isEmpty" == m.name && m.hasModifier(JvmModifier.PUBLIC) }) {
                reportVariable(mJavaContext, parentNode!!, isAffirmative!!)
                parentNode = null
                isAffirmative = null
            }
        }

        override fun visitBinaryExpression(node: UBinaryExpression) {
            val left: UElement = node.leftOperand
            if (!left.asSourceString().contains(".size()")) {
                return
            }
            val right: UExpression = node.rightOperand as? ULiteralExpression ?: return
            val rightOperand = right as ULiteralExpression
            if (rightOperand.value !is Number) {
                return
            }
            val rightValue = (rightOperand.value as Number?)!!.toDouble()
            if (UastBinaryOperator.IDENTITY_EQUALS == node.operator && rightValue == 0.0 || UastBinaryOperator.LESS == node.operator && rightValue == 1.0 || UastBinaryOperator.LESS_OR_EQUALS == node.operator && rightValue == 0.0) {
                // Memoize the node to check later and provide the fix also.
                parentNode = node
                isAffirmative = true
            } else if (UastBinaryOperator.GREATER == node.operator && rightValue == 0.0 ||
                UastBinaryOperator.GREATER_OR_EQUALS == node.operator && rightValue == 1.0
            ) {
                parentNode = node
                isAffirmative = false
            }
        }
    }
}
