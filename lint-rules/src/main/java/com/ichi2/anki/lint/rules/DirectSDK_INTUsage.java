package com.ichi2.anki.lint.rules;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UReferenceExpression;

import java.util.ArrayList;
import java.util.List;

public class DirectSDK_INTUsage extends Detector implements SourceCodeScanner {

    @VisibleForTesting
    static final String ID = "DirectSDK_INTUsage";

    @VisibleForTesting
    static final String DESCRIPTION = "Check whether SDK_INT is directly used.";

    private static final String EXPLANATION = "Check whether SDK_INT is directly used.";
    private static Implementation implementation = new Implementation(DirectSDK_INTUsage.class, Scope.JAVA_FILE_SCOPE);
    public static final Issue ISSUE = Issue.create(
            ID,
            DESCRIPTION,
            EXPLANATION,
            Constants.ANKI_CODE_STYLE_CATEGORY,
            Constants.ANKI_CODE_STYLE_PRIORITY,
            Constants.ANKI_CODE_STYLE_SEVERITY,
            implementation
    );

    public DirectSDK_INTUsage(){

    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        List<String> forbiddenReferenceNames = new ArrayList<>();
        forbiddenReferenceNames.add("SDK_INT");
        return forbiddenReferenceNames;
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement referenced){
        super.visitReference(context,reference,referenced);
        //JavaEvaluator evaluator = context.getEvaluator();
        //!LintUtils.isAnAllowedClass(foundClasses, "CompatHelper")
        UClass topClass = context.getUastFile().getClasses().get(0);
        PsiElement parent=referenced.getParent();
        PsiElement pParent=parent.getParent();

        if (parent.toString().contains("VERSION") && pParent.toString().contains("Build") && !topClass.toString().contains("CompatHelper")) {
            context.report(
                    ISSUE,
                    reference,
                    context.getLocation(referenced),
                    DESCRIPTION
            );
        }
    }


}
