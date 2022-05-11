package com.ichi2.anki.lint.rules

import com.android.tools.lint.detector.api.*
import com.google.common.annotations.VisibleForTesting
import com.ichi2.anki.lint.utils.Constants
import com.ichi2.anki.lint.utils.KotlinCleanup
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
@KotlinCleanup("IDE lint")
class DirectSystemTimeInstantiation : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        @VisibleForTesting
        val ID = "DirectSystemTimeInstantiation"

        @JvmField
        @VisibleForTesting
        val DESCRIPTION =
            "Use the collection's getTime() method instead of instantiating SystemTime"
        private const val EXPLANATION =
            "Creating SystemTime instances directly means time cannot be controlled during" +
                " testing, so it is not allowed. Use the collection's getTime() method instead"
        private val implementation = Implementation(
            DirectSystemTimeInstantiation::class.java, Scope.JAVA_FILE_SCOPE
        )
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

    override fun getApplicableConstructorTypes(): List<String>? {
        val forbiddenConstructors: MutableList<String> = ArrayList()
        forbiddenConstructors.add("com.ichi2.libanki.utils.SystemTime")
        return forbiddenConstructors
    }

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
