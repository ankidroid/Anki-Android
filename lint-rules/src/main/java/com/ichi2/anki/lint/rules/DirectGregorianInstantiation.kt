package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer creates [GregorianCalendar] instances directly
 * instead of using the collection's getTime() method.
 */
@KotlinCleanup("IDE lint")
@KotlinCleanup("mutableListOf")
class DirectGregorianInstantiation : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        @VisibleForTesting
        val ID = "DirectGregorianInstantiation"

        @JvmField
        @VisibleForTesting
        val DESCRIPTION = "Use the collection's getTime() method instead of directly creating GregorianCalendar instances"
        private const val EXPLANATION = "Creating GregorianCalendar instances directly is not allowed, as it " +
            "prevents control of time during testing. Use the collection's getTime() method instead"
        private val implementation = Implementation(DirectGregorianInstantiation::class.java, Scope.JAVA_FILE_SCOPE)
        @JvmField
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

    override fun getApplicableMethodNames(): List<String>? {
        val forbiddenMethods: MutableList<String> = ArrayList()
        forbiddenMethods.add("from")
        return forbiddenMethods
    }

    override fun getApplicableConstructorTypes(): List<String>? {
        val forbiddenConstructors: MutableList<String> = ArrayList()
        forbiddenConstructors.add("java.util.GregorianCalendar")
        return forbiddenConstructors
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time") && evaluator.isMemberInClass(method, "java.util.GregorianCalendar")) {
            context.report(
                ISSUE,
                context.getCallLocation(node, true, true),
                DESCRIPTION
            )
        }
    }

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        super.visitConstructor(context, node, constructor)
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                DESCRIPTION
            )
        }
    }
}
