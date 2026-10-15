// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/** Checks intent action constants. */
class IntentActionNamingDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableMethodNames() = listOf("setAction", "addAction")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        val owner = method.containingClass?.qualifiedName
        if (owner != "android.content.Intent" &&
            owner != "android.content.IntentFilter"
        ) {
            return
        }
        val argument = node.valueArguments.firstOrNull()?.skipParenthesizedExprDown() ?: return
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("ACTION_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Intent Action constants must start with ACTION_: ${field.name}",
        )
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "IntentActionNaming",
                briefDescription = "Intent action naming constants must start with ACTION_",
                explanation =
                    """
                    Intent actions constants should be named: ACTION_<DESCRIPTION>.
                    The name should be an action to represent the action. e.g. ACTION_MEDIA_EJECT.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(IntentActionNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
