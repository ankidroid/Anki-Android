package com.ichi2.anki.lint.rules;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.ichi2.anki.lint.utils.Constants;
import com.ichi2.anki.lint.utils.LintUtils;
import com.intellij.psi.PsiMethod;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

import java.util.ArrayList;
import java.util.List;

/**
 * This custom Lint rules will raise an error if a developer uses the {@link System#currentTimeMillis()} method instead
 * of using the time provided by the new Time class.
 */
public class DirectSystemCurrentTimeMillisUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectSystemCurrentTimeMillisUsage";
    @VisibleForTesting
    static final String DESCRIPTION = "Use the collection's getTime() method instead of System.currentTimeMillis()";
    private static final String EXPLANATION = "Using time directly means time values cannot be controlled during testing. " +
            "Time values like System.currentTimeMillis() must be obtained through the Time obtained from a Collection";
    private static final Implementation implementation = new Implementation(DirectSystemCurrentTimeMillisUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );


    public DirectSystemCurrentTimeMillisUsage() {
    }


    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        List<String> forbiddenSystemMethods = new ArrayList<>();
        forbiddenSystemMethods.add("currentTimeMillis");
        return forbiddenSystemMethods;
    }


    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        super.visitMethodCall(context, node, method);
        JavaEvaluator evaluator = context.getEvaluator();
        List<UClass> foundClasses = context.getUastFile().getClasses();
        if (!LintUtils.isAnAllowedClass(foundClasses, "SystemTime") && evaluator.isMemberInClass(method, "java.lang.System")) {
            context.report(
                    ISSUE,
                    context.getCallLocation(node, true, true),
                    DESCRIPTION
            );
        }
    }

}
