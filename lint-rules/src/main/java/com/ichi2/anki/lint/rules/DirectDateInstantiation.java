package com.ichi2.anki.lint.rules;

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
import org.jetbrains.uast.UExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * This custom Lint rules will raise an error if a developer instantiates the {@link java.util.Date} class directly
 * instead of using a {@link java.util.Date} provided through the collection's getTime() method.
 */
public class DirectDateInstantiation extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectDateInstantiation";
    @VisibleForTesting
    static final String DESCRIPTION = "Use the collection's getTime() method instead of directly instantiating Date";
    private static final String EXPLANATION = "Creating Date instances directly means dates cannot be controlled during" +
            " testing, so it is not allowed. Use the collection's getTime() method instead";
    private static final Implementation implementation = new Implementation(DirectDateInstantiation.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );


    public DirectDateInstantiation() {
    }


    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        List<String> forbiddenConstructors = new ArrayList<>();
        forbiddenConstructors.add("java.util.Date");
        return forbiddenConstructors;
    }


    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        super.visitConstructor(context, node, constructor);
        List<UClass> foundClasses = context.getUastFile().getClasses();
        // this checks for usage of new Date(ms) which we allow
        List<UExpression> argTypes = node.getValueArguments();
        if (argTypes != null && argTypes.size() == 1) {
            UExpression onlyArgument = argTypes.get(0);
            if (onlyArgument != null && onlyArgument.getExpressionType().equalsToText("long")) {
                return;
            }
        }
        if (!LintUtils.isAnAllowedClass(foundClasses, "Time")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    DESCRIPTION
            );
        }
    }

}
