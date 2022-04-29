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
import java.util.Calendar;
import java.util.List;

/**
 * This custom Lint rules will raise an error if a developer uses the {@link Calendar#getInstance()} method instead
 * of using the {@link Calendar} provided by the collection's getTime() method.
 */
public class DirectCalendarInstanceUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectCalendarInstanceUsage";
    @VisibleForTesting
    static final String DESCRIPTION = "Use the collection's getTime() method instead of directly creating Calendar instances";
    private static final String EXPLANATION = "Manually creating Calendar instances means time cannot be controlled " +
            "during testing. Calendar instances must be obtained through the collection's getTime() method";
    private static final Implementation implementation = new Implementation(DirectCalendarInstanceUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );


    public DirectCalendarInstanceUsage() {
    }


    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        List<String> forbiddenMethods = new ArrayList<>();
        forbiddenMethods.add("getInstance");
        return forbiddenMethods;
    }


    @Override
    public void visitMethodCall(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
        super.visitMethodCall(context, node, method);
        JavaEvaluator evaluator = context.getEvaluator();
        List<UClass> foundClasses = context.getUastFile().getClasses();
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time") && evaluator.isMemberInClass(method, "java.util.Calendar")) {
            context.report(
                    ISSUE,
                    context.getCallLocation(node, true, true),
                    DESCRIPTION
            );
        }
    }

}
