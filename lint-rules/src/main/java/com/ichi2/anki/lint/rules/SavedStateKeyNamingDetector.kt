// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.DataFlowAnalyzer
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isReturningContext
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.ext.resolveToField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.skipParenthesizedExprDown

class SavedStateKeyNamingDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name !in SAVED_STATE_METHODS) return
                val body = node.uastBody ?: return
                node.uastParameters
                    .filter { it.type.canonicalText == "android.os.Bundle" }
                    .forEach { checkBundle(context, body, it) }
            }
        }

    private fun checkBundle(
        context: JavaContext,
        scope: UElement,
        bundle: UElement,
    ) {
        val keys = mutableListOf<UExpression>()
        scope.accept(
            object : DataFlowAnalyzer(listOf(bundle)) {
                override fun returnsSelf(call: UCallExpression) = isReturningContext(call)

                override fun receiver(call: UCallExpression) {
                    val method = call.resolve() ?: return
                    call.keyArgument(context, method)?.let(keys::add)
                }

                override fun argument(
                    call: UCallExpression,
                    reference: UElement,
                ) {
                    val method = call.resolve() ?: return
                    if (method.containingClass?.qualifiedName !in BUNDLE_HELPERS) return
                    val bundleIndex = method.parameterList.parameters.indexOfFirst { it.type.canonicalText == "android.os.Bundle" }
                    if (bundleIndex < 0 || call.getArgumentForParameter(bundleIndex) != reference) return
                    call.keyArgument(context, method)?.let(keys::add)
                }
            },
        )
        keys.distinctBy { it.sourcePsi }.forEach { checkKey(context, it) }
    }

    private fun UCallExpression.keyArgument(
        context: JavaContext,
        method: PsiMethod,
    ): UExpression? {
        val isBundleMember =
            context.evaluator.isMemberInClass(method, "android.os.Bundle") ||
                context.evaluator.isMemberInClass(method, "android.os.BaseBundle")
        if (!isBundleMember && method.containingClass?.qualifiedName !in BUNDLE_HELPERS) return null
        val keyIndex = method.parameterList.parameters.indexOfFirst { it.type.canonicalText == "java.lang.String" }
        if (keyIndex < 0) return null
        return getArgumentForParameter(keyIndex)
    }

    private fun checkKey(
        context: JavaContext,
        expression: UExpression,
    ) {
        val argument = expression.skipParenthesizedExprDown()
        val field = argument.resolveToField() ?: return
        if (!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL)) return
        if (field.name.startsWith("STATE_")) return
        if (ConstantEvaluator.evaluate(context, argument) !is String) return
        context.report(
            ISSUE,
            argument,
            context.getLocation(argument),
            "Saved instance state key constants must start with STATE_: ${field.name}",
        )
    }

    companion object {
        private val SAVED_STATE_METHODS =
            setOf(
                "onSaveInstanceState",
                "onRestoreInstanceState",
                "onViewStateRestored",
                "onCreate",
                "onCreateView",
                "onViewCreated",
                "onActivityCreated",
                "onCreateDialog",
                "onPostCreate",
                "onCreatePreferences",
            )

        private val BUNDLE_HELPERS =
            setOf(
                "androidx.core.os.BundleCompat",
                "com.ichi2.anki.utils.ext.BundleUtilsKt",
                "com.ichi2.anki.compat.CompatHelper.Companion",
                "com.ichi2.anki.compat.CompatHelperKt",
            )

        val ISSUE =
            Issue.create(
                id = "SavedStateKeyNaming",
                briefDescription = "Saved instance state keys must start with STATE_",
                explanation =
                    """
                    Saved instance state key constants should be named STATE_<DESCRIPTION>,
                    for example STATE_SCROLL_POSITION.
                    """.trimIndent(),
                category = Constants.ANKI_CODE_STYLE_CATEGORY,
                priority = Constants.ANKI_CODE_STYLE_PRIORITY,
                severity = Constants.ANKI_CODE_STYLE_SEVERITY,
                implementation = Implementation(SavedStateKeyNamingDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
