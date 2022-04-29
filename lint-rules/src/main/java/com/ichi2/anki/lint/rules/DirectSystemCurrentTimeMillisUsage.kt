package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
import com.ichi2.anki.lint.utils.LintUtils
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * This custom Lint rules will raise an error if a developer uses the [System.currentTimeMillis] method instead
 * of using the time provided by the new Time class.
 */
@KotlinCleanup("IDE lint")
@KotlinCleanup("mutableListOf")
class DirectSystemCurrentTimeMillisUsage : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        @VisibleForTesting
        val ID = "DirectSystemCurrentTimeMillisUsage"

        @JvmField
        @VisibleForTesting
        val DESCRIPTION = "Use the collection's getTime() method instead of System.currentTimeMillis()"
        private const val EXPLANATION = "Using time directly means time values cannot be controlled during testing. " +
            "Time values like System.currentTimeMillis() must be obtained through the Time obtained from a Collection"
        private val implementation = Implementation(DirectSystemCurrentTimeMillisUsage::class.java, Scope.JAVA_FILE_SCOPE)
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
        val forbiddenSystemMethods: MutableList<String> = ArrayList()
        forbiddenSystemMethods.add("currentTimeMillis")
        return forbiddenSystemMethods
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        super.visitMethodCall(context, node, method)
        val evaluator = context.evaluator
        val foundClasses = context.uastFile!!.classes
        if (!LintUtils.isAnAllowedClass(foundClasses, "SystemTime") && evaluator.isMemberInClass(method, "java.lang.System")) {
            context.report(
                ISSUE,
                context.getCallLocation(node, true, true),
                DESCRIPTION
            )
        }
    }
}
