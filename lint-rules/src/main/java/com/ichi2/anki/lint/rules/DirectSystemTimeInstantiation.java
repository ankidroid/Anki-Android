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

import java.util.ArrayList;
import java.util.List;

/**
 * This custom Lint rules will raise an error if a developer instantiates the SystemTime class directly
 * instead of using the Time class from a Collection.
 * <br />
 * NOTE: For future reference, if you plan on creating a Lint rule which looks for a constructor invocation, make sure
 * that the target class has a constructor defined in its source code!
 */
public class DirectSystemTimeInstantiation extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectSystemTimeInstantiation";
    @VisibleForTesting
    static final String DESCRIPTION = "Use the collection's getTime() method instead of instantiating SystemTime";
    private static final String EXPLANATION = "Creating SystemTime instances directly means time cannot be controlled during" +
            " testing, so it is not allowed. Use the collection's getTime() method instead";
    private static final Implementation implementation = new Implementation(DirectSystemTimeInstantiation.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_TIME_CATEGORY,
            Constants.ANKI_TIME_PRIORITY,
            Constants.ANKI_TIME_SEVERITY,
            implementation
    );


    public DirectSystemTimeInstantiation() {

    }


    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        List<String> forbiddenConstructors = new ArrayList<>();
        forbiddenConstructors.add("com.ichi2.libanki.utils.SystemTime");
        return forbiddenConstructors;
    }


    @Override
    public void visitConstructor(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod constructor) {
        super.visitConstructor(context, node, constructor);
        List<UClass> foundClasses = context.getUastFile().getClasses();
        if (!LintUtils.isAnAllowedClass(foundClasses, "Storage", "CollectionHelper")) {
            context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    DESCRIPTION
            );
        }
    }

}
